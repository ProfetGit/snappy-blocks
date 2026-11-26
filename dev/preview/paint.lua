-- Original art for the settings previews (vanilla style, light from the top left): a copper pickaxe (only the
-- stand-in the swing is animated on: the game draws its own tools), and
-- the throwing arm's 32 x 16 atlas (sleeve side, sleeve top, then hand side, hand end, cuff side, cuff end).
-- Run: aseprite --batch --script dev/preview/paint.lua   (from mods/SnappyBlocks)
local dir = "/home/emppu/Projects/Minecraft Datapacks/mods/SnappyBlocks/dev/preview/tex/"

local function hex(s)
  return app.pixelColor.rgba(tonumber(s:sub(1, 2), 16), tonumber(s:sub(3, 4), 16), tonumber(s:sub(5, 6), 16), 255)
end

local function sprite(name, rows, pal)
  local h, w = #rows, #rows[1]
  local spr = Sprite(w, h, ColorMode.RGB)
  local img = spr.cels[1].image
  for y = 1, h do
    assert(#rows[y] == w, name .. " row " .. y .. " is " .. #rows[y])
    for x = 1, w do
      local ch = rows[y]:sub(x, x)
      if ch ~= "." then
        assert(pal[ch], name .. ": no colour " .. ch)
        img:drawPixel(x - 1, y - 1, hex(pal[ch]))
      end
    end
  end
  spr:saveAs(dir .. name .. ".aseprite")
  spr:saveCopyAs(dir .. name .. ".png")
  spr:close()
end

-- copper pickaxe: head bar and leg, a 3 px diagonal handle; dark rim of its own hue, highlights top left
local pick = { o = "5a2414", a = "8f3d22", b = "b95a33", c = "dc7a4a", d = "f6a878", e = "ffd9b8",
  k = "2e1c10", l = "553820", m = "7d5530", n = "9f7340" }
sprite("copper_pickaxe", {
  "................",
  "....ooooooo.....",
  "...oeeddccbo....",
  "...odccbbbbboko.",
  "....ooooooabbmk.",
  "..........knbao.",
  ".........knmkbco",
  "........knmk.obo",
  ".......knmk..obo",
  "......knmk...oao",
  ".....knmk....oao",
  "....knmk......o.",
  "...knmk.........",
  "..knmk..........",
  ".knmk...........",
  ".kkk............",
}, pick)

local arm = { q = "6f88bd", r = "566ea3", s = "45598a", t = "34446b", u = "27334f",
  f = "eec09a", g = "d9a27c", h = "c08863", i = "9c6a4d", j = "7a4f3a",
  a = "8f3d22", b = "b95a33", c = "dc7a4a", d = "f6a878" }
sprite("arm", {
  "qqqrqqqqqrqqqqrq................",
  "rrrrrsrrrrrrrsrr................",
  "rrsrrrrrrsrrrrrr................",
  "sssrssssssssrsss................",
  "tttttutttttttutt................",
  "qrqqqqqrqqqqqrqq................",
  "qqqqrqqqqqrqqqqq................",
  "rqqqqqrqqqqqqqrq................",
  "qqrqqqqqqrqqrqqq................",
  "rrrrsrrrrrrrrsrr................",
  "fffffgggggddddddcccccd..........",
  "ggfggghhhhccccddcddccd..........",
  "ggggggiiiicccccccccccc..........",
  "hhghhghhhhbbbbbbccbbcc..........",
  "iiiiiijjjjbbbbbbcccccc..........",
  "..........aaaaaabbbbbb..........",
}, arm)
print("painted")
