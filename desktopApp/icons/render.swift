import AppKit

// Renders the SVG icon to a PNG with the macOS drop shadow (AppKit's SVG support skips filters).
// Usage: render <icon.svg> <size> <out.png>
let args = CommandLine.arguments
let size = Int(args[2])!
guard let svg = NSImage(contentsOf: URL(fileURLWithPath: args[1])) else { fatalError("Cannot load \(args[1])") }
let rep = NSBitmapImageRep(
    bitmapDataPlanes: nil, pixelsWide: size, pixelsHigh: size, bitsPerSample: 8, samplesPerPixel: 4,
    hasAlpha: true, isPlanar: false, colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)!
NSGraphicsContext.saveGraphicsState()
NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: rep)
NSGraphicsContext.current?.imageInterpolation = .high
let scale = CGFloat(size) / 1024
let shadow = NSShadow()
shadow.shadowOffset = NSSize(width: 0, height: -12 * scale)
shadow.shadowBlurRadius = 28 * scale
shadow.shadowColor = NSColor.black.withAlphaComponent(0.35)
shadow.set()
svg.draw(in: NSRect(x: 0, y: 0, width: size, height: size))
NSGraphicsContext.restoreGraphicsState()
try! rep.representation(using: .png, properties: [:])!.write(to: URL(fileURLWithPath: args[3]))
