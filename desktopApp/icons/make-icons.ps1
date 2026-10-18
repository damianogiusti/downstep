# Rebuilds downstep.ico (Windows) and downstep.png (Linux) from downstep.svg. Needs resvg on the PATH
# (cargo install resvg). downstep.icns needs macOS's iconutil: make-icons.sh there.
$ErrorActionPreference = 'Stop'

function Render([string]$svg, [int]$size, [string]$out) {
    & resvg -w $size -h $size (Join-Path $PSScriptRoot $svg) $out
    if ($LASTEXITCODE -ne 0) { throw "resvg failed on $svg at $size px" }
}

$work = Join-Path ([IO.Path]::GetTempPath()) "downstep-icons-$([Guid]::NewGuid())"
New-Item -ItemType Directory $work | Out-Null
try {
    # Windows: the sizes Explorer, the taskbar and Alt+Tab ask for, from 100 to 200 % scaling.
    $sizes = 16, 20, 24, 32, 40, 48, 64, 128, 256
    $pngs = [Collections.Generic.List[byte[]]]::new()
    foreach ($s in $sizes) {
        $png = Join-Path $work "ico_$s.png"
        Render 'downstep.svg' $s $png
        $pngs.Add([IO.File]::ReadAllBytes($png))
    }

    # An .ico of PNG entries (Vista and later), as make-ico.py writes it.
    $buffer = [IO.MemoryStream]::new()
    $out = [IO.BinaryWriter]::new($buffer)
    $out.Write([uint16]0); $out.Write([uint16]1); $out.Write([uint16]$sizes.Count)
    $offset = 6 + 16 * $sizes.Count
    for ($i = 0; $i -lt $sizes.Count; $i++) {
        # 0 means 256 in the one-byte width and height fields.
        $out.Write([byte]($sizes[$i] % 256)); $out.Write([byte]($sizes[$i] % 256))
        $out.Write([byte]0); $out.Write([byte]0)
        $out.Write([uint16]1); $out.Write([uint16]32)
        $out.Write([uint32]$pngs[$i].Length); $out.Write([uint32]$offset)
        $offset += $pngs[$i].Length
    }
    foreach ($png in $pngs) { $out.Write($png) }
    $out.Flush()
    [IO.File]::WriteAllBytes((Join-Path $PSScriptRoot 'downstep.ico'), $buffer.ToArray())

    Render 'downstep.svg' 512 (Join-Path $PSScriptRoot 'downstep.png')
} finally {
    Remove-Item -Recurse -Force $work
}
