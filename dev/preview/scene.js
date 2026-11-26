// The settings previews' tool swings (animated here, drawn live in game) and the throwing arm (modelled, animated and
// rendered here), in Blockbench.
// Run: python3 tools/Assets/bb.py run mods/SnappyBlocks/dev/preview/scene.js --owner snappy-preview --hold 300
// Then: python3 -I mods/SnappyBlocks/dev/preview/compose.py
(async function () {
const fs = require('fs');
const DIR = '/home/emppu/Projects/Minecraft Datapacks/mods/SnappyBlocks/dev/preview/';
const OUT = DIR + 'out/';
// screen pixels: 16 units (a block) is 64 px along each ground axis' x, 32 along its y, 80 px up (2:1 dimetric)
const BOX_W = 396, BOX_H = 300, AX = 198, AY = 142;
const S = 128 / (16 * Math.SQRT2), YSCALE = 5 / (Math.cos(Math.PI / 6) * S);
const PICK = {pivot: [24.4, 10.4, -4.6], yaw: 24, grip: [2.5, 13.5]};
// the arm points to screen right from a shoulder off the frame; {release} is the frame the fist lets go
const ARM = {shoulder: [-30.85, 10.2, 23.45], yaw: 30, release: 15};

function dataUrl(name) { return 'data:image/png;base64,' + fs.readFileSync(DIR + 'tex/' + name + '.png').toString('base64'); }
function load(url) { return new Promise((res, rej) => { const i = new Image(); i.onload = () => res(i); i.onerror = rej; i.src = url; }); }

async function project(name) {
  ModelProject.all.filter(p => p.name === name).forEach(p => { p.select(); p.close(true); });
  newProject(Formats.free);
  Project.name = name;
  Project.texture_width = 16;
  Project.texture_height = 16;
}

async function texture(name) {
  const t = new Texture({name: name + '.png'}).fromDataURL(dataUrl(name)).add(false);
  const img = await load(dataUrl(name));
  t.uv_width = img.width;
  t.uv_height = img.height;
  const three = new THREE.Texture(img);
  three.magFilter = three.minFilter = THREE.NearestFilter;
  three.generateMipmaps = false;
  three.needsUpdate = true;
  return {bb: t, img, three};
}

function cube(name, from, to, faces, extra) {
  const all = {};
  for (const f of ['north', 'south', 'east', 'west', 'up', 'down']) {
    const d = faces[f];
    all[f] = d ? {texture: d[0].bb.uuid, uv: d[1]} : {texture: null, uv: [0, 0, 0, 0]};
  }
  const c = new Cube(Object.assign({name, from, to, faces: all, box_uv: false, autouv: 0}, extra || {}));
  c.userData_tex = faces;
  return c;
}

// ---- renderer ----
const VERT = 'varying vec2 vUv; varying vec3 vN;' +
  'void main(){ vUv = uv; vN = normalize(mat3(modelMatrix) * normal); gl_Position = projectionMatrix * viewMatrix * modelMatrix * vec4(position, 1.0); }';
// warm light from the upper front, cool shade
const FRAG = 'uniform sampler2D map; varying vec2 vUv; varying vec3 vN;' +
  'void main(){ vec4 t = texture2D(map, vUv); if (t.a < 0.5) discard; vec3 L = normalize(vec3(0.25, 0.85, 0.55)); float d = max(dot(normalize(vN), L), 0.0);' +
  ' vec3 lit = mix(vec3(0.66, 0.60, 0.70), vec3(1.22, 1.14, 1.02), clamp(d * 1.15, 0.0, 1.0)); gl_FragColor = vec4(t.rgb * lit, 1.0); }';

function renderer(w, h) {
  const r = new THREE.WebGLRenderer({antialias: false, alpha: true, preserveDrawingBuffer: true, premultipliedAlpha: false});
  r.setPixelRatio(1);
  r.setSize(w, h);
  r.setClearColor(0x000000, 0);
  return r;
}

// draws the project's cubes (textures looked up per face) into r, with (AX, AY) of a BOX_W x BOX_H frame at the origin
function draw(r) {
  const scene = new THREE.Scene(), root = new THREE.Group();
  root.scale.set(1, YSCALE, 1);
  scene.add(root);
  Canvas.scene.updateMatrixWorld(true);
  const inv = new THREE.Matrix4();
  for (const c of Cube.all) {
    const mesh = c.mesh;
    mesh.updateWorldMatrix(true, false);
    const geo = mesh.geometry.clone();
    const mats = Canvas.face_order.map(f => {
      const d = c.userData_tex[f];
      return d ? new THREE.ShaderMaterial({uniforms: {map: {value: d[0].three}}, vertexShader: VERT, fragmentShader: FRAG, side: THREE.DoubleSide})
        : new THREE.MeshBasicMaterial({visible: false});
    });
    // Blockbench drops the disabled faces from the index buffer: rebuild it, 6 indices per face in face order
    const index = [];
    for (let i = 0; i < 6; i++) index.push(4 * i, 4 * i + 2, 4 * i + 1, 4 * i + 2, 4 * i + 3, 4 * i + 1);
    geo.setIndex(index);
    geo.clearGroups();
    for (let i = 0; i < 6; i++) geo.addGroup(i * 6, 6, i);
    const m = new THREE.Mesh(geo, mats);
    m.matrixAutoUpdate = false;
    m.matrix.copy(mesh.matrixWorld);
    root.add(m);
  }
  const cam = new THREE.OrthographicCamera(-AX / S, (BOX_W - AX) / S, AY / S, -(BOX_H - AY) / S, 1, 2000);
  const el = Math.PI / 6, az = Math.PI / 4, d = 500;
  cam.position.set(Math.cos(el) * Math.sin(az) * d, Math.sin(el) * d, Math.cos(el) * Math.cos(az) * d);
  cam.lookAt(0, 0, 0);
  cam.updateProjectionMatrix();
  r.render(scene, cam);
  return r.domElement.toDataURL('image/png');
}

function save(path, url) { fs.writeFileSync(path, Buffer.from(url.split(',')[1], 'base64')); }

// ---- rendering a project's clips: [time s, rotation z (counter-clockwise positive), position x, y] ----
let r = null;
const meta = {};
function animate(group, yaw, clips, name, each, render) {
  for (const [clipName, clip] of Object.entries(clips)) {
    const anim = new Animation({name: clipName, length: clip.len, loop: clip.loop, snapping: 60}).add(false);
    anim.select();
    const bone = anim.getBoneAnimator(group);
    for (const [t, a, dx, dy] of clip.keys) {
      bone.addKeyframe({channel: 'rotation', time: t, interpolation: 'catmullrom', data_points: [{x: 0, y: yaw, z: a}]});
      bone.addKeyframe({channel: 'position', time: t, interpolation: 'catmullrom', data_points: [{x: dx, y: dy, z: 0}]});
    }
  }
  Modes.options.animate.select();
  r = renderer(BOX_W * 2, BOX_H * 2);
  for (const [clipName, clip] of Object.entries(clips)) {
    Animation.all.find(a => a.name === clipName).select();
    const frames = Math.round(clip.len * 60) + (clip.loop === 'once' ? 1 : 0);
    meta[name + '_' + clipName] = frames;
    for (let i = 0; i < frames; i++) {
      Timeline.setTime(i / 60);
      Animator.preview();
      if (render !== false) save(OUT + name + '_' + clipName + '_' + String(i).padStart(2, '0') + '.png', draw(r));
      if (each) each(clipName, i);
    }
  }
  r.dispose();
  r.forceContextLoss();
  Timeline.setTime(0);
  Modes.options.edit.select();
  fs.writeFileSync(DIR + name + '.bbmodel', Codecs.project.compile());
}

// [time s, swing angle, pivot lift x, y]
const TOOL_CLIPS = {
  swing: {len: 0.3, loop: 'loop', keys: [[0, 18, 0, 0], [0.0333, 12, 0.3, 0.2], [0.0833, -12, 1.0, 0.8], [0.15, -48, 2.0, 1.8],
    [0.2, -63, 2.4, 2.4], [0.2333, -60, 2.3, 2.3], [0.2667, -30, 1.2, 1.2], [0.3, 18, 0, 0]]},
  // the block gives way: a short bite further in, then straight back out of the picture's middle
  follow: {len: 0.5, loop: 'once', keys: [[0, 18, 0, 0], [0.0333, 24, -0.3, -0.5], [0.1, -18, 2.6, 0.9], [0.1667, -44, 4.2, 2.0],
    [0.2667, -40, 3.2, 1.8], [0.3833, -35, 1.7, 1.3], [0.5, -36, 1.6, 1.3]]},
  // from rest up over the head and into the first hit
  raise: {len: 0.25, loop: 'once', keys: [[0, -36, 1.6, 1.3], [0.0667, -52, 2.1, 2.0], [0.1333, -65, 2.5, 2.5], [0.1667, -61, 2.4, 2.4],
    [0.2167, -30, 1.2, 1.2], [0.25, 18, 0, 0]]},
  // mining, once every 4 ticks as the hits come: a shorter backswing than vanilla's 6 tick swing, with the strike
  // spread over four frames
  mine: {len: 0.2, loop: 'loop', keys: [[0, 18, 0, 0], [0.0333, 9, 0.3, 0.3], [0.0667, -20, 1.2, 1.0], [0.1, -43, 1.9, 1.7],
    [0.1333, -49, 2.1, 2.0], [0.1667, -20, 1.0, 0.9], [0.2, 18, 0, 0]]}
};

// ---- the tools' swings ----
// The game draws the player's own copper pickaxe and axe, so only the motion is made here: a stand-in pickaxe of our
// own art is animated, and each frame's move from its rest pose is written out as a 3 x 4 matrix in model space.
{
  await project('snappy_preview_swing');
  const tex = await texture('copper_pickaxe');
  const tool = new Group({name: 'tool', origin: PICK.pivot}).init();
  const cv = document.createElement('canvas');
  cv.width = cv.height = 16;
  const cx = cv.getContext('2d');
  cx.drawImage(tex.img, 0, 0);
  const alpha = cx.getImageData(0, 0, 16, 16).data;
  const solid = (u, v) => u >= 0 && v >= 0 && u < 16 && v < 16 && alpha[(v * 16 + u) * 4 + 3] > 127;
  for (let v = 0; v < 16; v++) for (let u = 0; u < 16; u++) {
    if (!solid(u, v)) continue;
    // mirrored: the grip is at the lower right, the head swings in from the upper right towards -x
    const x = PICK.pivot[0] - (u + 0.5 - PICK.grip[0]) - 0.5, y = PICK.pivot[1] + (PICK.grip[1] - v - 0.5) - 0.5, z = PICK.pivot[2] - 0.6;
    const px = [tex, [u, v, u + 1, v + 1]], flip = [tex, [u + 1, v, u, v + 1]];
    cube('t' + u + '_' + v, [x, y, z], [x + 1, y + 1, z + 1.2], {south: flip, north: px,
      up: solid(u, v - 1) ? null : px, down: solid(u, v + 1) ? null : px,
      east: solid(u - 1, v) ? null : px, west: solid(u + 1, v) ? null : px}).addTo(tool).init();
  }
  Canvas.updateAll();
  Canvas.scene.updateMatrixWorld(true);
  const rest = tool.mesh.matrixWorld.clone().invert();
  meta.motion = [];
  animate(tool, PICK.yaw, TOOL_CLIPS, 'swing', (clip, i) => {
    Canvas.scene.updateMatrixWorld(true);
    // column major to three rows of four
    const e = tool.mesh.matrixWorld.clone().multiply(rest).elements;
    meta.motion.push([e[0], e[4], e[8], e[12], e[1], e[5], e[9], e[13], e[2], e[6], e[10], e[14]].map(n => Math.round(n * 1e5) / 1e5));
  }, false);
}

// ---- the throwing arm: sleeve, copper cuff and a fist, reaching in from beyond the left edge ----
{
  await project('snappy_preview_arm');
  Project.texture_width = 32;
  const skin = await texture('arm');
  const O = ARM.shoulder;
  const yawGroup = new Group({name: 'yaw', origin: O, rotation: [0, ARM.yaw, 0]}).init();
  const arm = new Group({name: 'arm', origin: O}).addTo(yawGroup).init();
  const at = (x, y, z) => [O[0] + x, O[1] + y, O[2] + z];
  const uv = (u0, v0, u1, v1) => [skin, [u0, v0, u1, v1]];
  const sleeve = uv(0, 0, 16, 5), top = uv(0, 5, 16, 10), hand = uv(0, 10, 5, 15), end = uv(5, 10, 10, 15), cuff = uv(10, 10, 12, 16);
  cube('sleeve', at(-2, -2.5, -2.5), at(14, 2.5, 2.5), {south: sleeve, north: sleeve, up: top, down: sleeve, west: uv(0, 0, 5, 5)}).addTo(arm).init();
  cube('cuff', at(14, -3, -3), at(16, 3, 3), {south: cuff, north: cuff, up: cuff, down: cuff, east: uv(16, 10, 22, 16), west: uv(16, 10, 22, 16)}).addTo(arm).init();
  const fist = cube('hand', at(16, -2.5, -2.5), at(21, 2.5, 2.5), {south: hand, north: hand, up: hand, down: hand, east: end});
  fist.addTo(arm).init();
  const thumb = uv(0, 10, 3, 12);
  cube('thumb', at(16.5, -1, 2.5), at(20, 1.2, 3.7), {south: thumb, up: thumb, down: uv(0, 13, 3, 15), east: uv(0, 12, 1, 14), west: thumb}).addTo(arm).init();
  Canvas.updateAll();
  const CLIPS = {throw: {len: 0.5, loop: 'once', keys: [[0, -12, -1, 0], [0.1, 4, -2.5, 0], [0.1667, 9, -3, 0], [0.2167, 2, -2, 0],
    [0.25, -8, 2, 0], [0.3, -18, 3.5, 0], [0.4, -17, 1.5, 0], [0.5, -12, -1, 0]]}};
  // where the fist is on screen when it lets go, in frame pixels
  animate(arm, 0, CLIPS, 'arm', (clip, i) => {
    if (i !== ARM.release) return;
    Canvas.scene.updateMatrixWorld(true);
    fist.mesh.geometry.computeBoundingBox();
    const p = fist.mesh.geometry.boundingBox.getCenter(new THREE.Vector3()).applyMatrix4(fist.mesh.matrixWorld);
    const q = p.clone();
    q.y *= YSCALE;
    meta.fist = [AX + (q.x - q.z) * S / Math.SQRT2, AY + (q.x + q.z) * S / Math.SQRT2 * Math.sin(Math.PI / 6) - q.y * S * Math.cos(Math.PI / 6)];
    meta.fistModel = [p.x, p.y, p.z];
  });
}

fs.writeFileSync(OUT + 'meta.json', JSON.stringify(meta));
const ids = ModelProject.all.filter(p => p.name.startsWith('snappy_preview_'));
ids.forEach(p => { p.select(); p.close(true); });
return JSON.stringify({ok: true, meta});
})()
