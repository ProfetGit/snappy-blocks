-- Placing dust sprites (16 px = 1 block, binary alpha; the game tints them and sets the see-through).
-- Run from the mod folder: aseprite --batch --script dev/art/dust/dust.lua
local OUT = "src/main/resources/assets/snappy_blocks/textures/particle/"
local SRC = "dev/art/dust/"
local C = {
  Color{r=255,g=255,b=255}, Color{r=236,g=233,b=226},
  Color{r=210,g=205,b=194}, Color{r=181,g=174,b=161},
}

-- A cloud is a union of lumps {x, y, r}; holes {x, y, r} bite it apart. Each pixel is lit by the lump it sits
-- deepest in: from the top left, with a shaded underside.
local function cloud(img, ox, size, lumps, holes)
  for y = 0, size - 1 do
    for x = 0, size - 1 do
      local px, py, best, lump = x + 0.5, y + 0.5, 0, nil
      for _, l in ipairs(lumps) do
        local depth = l[3] - math.sqrt((px - l[1])^2 + (py - l[2])^2)
        if depth > best then best, lump = depth, l end
      end
      for _, h in ipairs(holes or {}) do
        if math.sqrt((px - h[1])^2 + (py - h[2])^2) < h[3] then lump = nil end
      end
      if lump then
        local lit = -((px - lump[1]) * 0.55 + (py - lump[2]) * 0.83) / lump[3]
        local c = lit > 0.42 and 1 or lit > -0.12 and 2 or lit > -0.58 and 3 or 4
        img:drawPixel(ox + x, y, C[c])
      end
    end
  end
end

local function sheet(name, size, frames, paint)
  local sprite = Sprite(size, size, ColorMode.RGB)
  for i = 2, #frames do sprite:newEmptyFrame(i) end
  local strip = Image(size * #frames, size, ColorMode.RGB)
  for i, f in ipairs(frames) do
    local img = Image(size, size, ColorMode.RGB)
    paint(img, 0, size, f)
    paint(strip, (i - 1) * size, size, f)
    sprite:newCel(sprite.layers[1], i, img)
    sprite.frames[i].duration = 0.08
  end
  strip:saveAs(OUT .. name .. ".png")
  sprite:saveAs(SRC .. name .. ".aseprite")
  sprite:close()
end

-- Poof: a tight ball swells into a three-lobed cloud, then tears into wisps that dwindle.
sheet("dust_poof", 12, {
  { {{6, 7.5, 2.4}} },
  { {{4.6, 7.4, 2.6}, {7.6, 6.8, 3.0}, {6.2, 8.4, 2.4}} },
  { {{3.9, 7.3, 3.1}, {7.9, 6.0, 3.8}, {6.6, 8.6, 3.0}, {5.4, 4.8, 2.3}} },
  { {{3.4, 7.2, 3.2}, {8.4, 5.7, 3.7}, {7.0, 8.7, 2.9}, {5.0, 4.0, 2.7}}, {{6.4, 6.9, 1.2}} },
  { {{3.0, 7.0, 2.8}, {8.9, 5.2, 3.0}, {7.4, 9.0, 2.4}, {4.8, 3.4, 2.2}}, {{6.2, 6.6, 2.3}, {9.6, 7.6, 1.0}} },
  { {{2.6, 6.6, 2.1}, {9.3, 4.6, 2.3}, {7.8, 9.3, 1.8}, {4.4, 2.8, 1.6}}, {{6.2, 6.4, 2.8}, {2.4, 7.8, 0.9}, {9.8, 3.4, 0.8}} },
  { {{2.3, 6.1, 1.4}, {9.7, 4.1, 1.5}, {8.0, 9.6, 1.1}, {4.2, 2.3, 1.0}} },
  { {{2.0, 5.6, 0.8}, {10.0, 3.6, 0.8}, {8.3, 9.9, 0.7}} },
}, function(img, ox, size, f) cloud(img, ox, size, f[1], f[2]) end)

-- Wisp: the small lumps that race along the front of the shockwave.
sheet("dust_wisp", 6, {
  { {{3, 3.4, 2.7}, {2.2, 3.6, 2.0}} },
  { {{2.5, 3.5, 2.1}, {4.0, 2.7, 1.8}} },
  { {{2.0, 3.8, 1.4}, {4.4, 2.3, 1.2}} },
  { {{1.6, 4.1, 0.8}, {4.7, 2.0, 0.7}} },
}, function(img, ox, size, f) cloud(img, ox, size, f[1], f[2]) end)

-- Shockwave: a ring on the ground, seen from above (48 px = 3 blocks), that rolls outward from under the block,
-- thickest just after it leaves the block, then breaks into clumps. {radius, lump radius, lumps kept out of 26}
local JIT = {0, 0.8, -0.5, 0.4, -0.9, 0.2, 0.9, -0.3, 0.5, -0.7, 0.1, 0.7, -0.4, 0.3, -0.8, 0.6, -0.1, 0.9, -0.6, 0.2,
  -0.2, 0.8, -0.7, 0.4, -0.5, 0.6}
local ORDER = {1, 14, 7, 20, 4, 17, 10, 23, 2, 15, 8, 21, 5, 18, 11, 24, 3, 16, 9, 22, 6, 19, 12, 25, 13, 26}
sheet("dust_ring", 48, {
  {10.5, 2.3, 26}, {13.5, 3.0, 26}, {16.0, 3.3, 26}, {18.0, 3.0, 24},
  {19.5, 2.5, 21}, {20.8, 2.0, 17}, {21.7, 1.5, 13}, {22.3, 1.0, 9},
}, function(img, ox, size, f)
  local lumps = {}
  for k = 1, f[3] do
    local i = ORDER[k]
    local a = (i - 1) / 26 * 2 * math.pi + JIT[(i * 7) % 26 + 1] * 0.07
    local r = f[1] + JIT[i] * 0.9
    local w = f[2] * (1 + JIT[(i * 3) % 26 + 1] * 0.3)
    lumps[#lumps + 1] = {24 + math.cos(a) * r, 24 + math.sin(a) * r, w}
    -- a trailing lump inside the front gives the ring its depth while it is young
    if f[2] > 2.4 and i % 2 == 0 then
      lumps[#lumps + 1] = {24 + math.cos(a + 0.1) * (r - w), 24 + math.sin(a + 0.1) * (r - w), w * 0.65}
    end
  end
  cloud(img, ox, size, lumps)
end)
