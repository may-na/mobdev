// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "CalculatorEngine",
    platforms: [.iOS(.v17), .macOS(.v13)],
    products: [
        .library(name: "CalculatorEngine", targets: ["CalculatorEngine"])
    ],
    targets: [
        .target(name: "CalculatorEngine"),
        .testTarget(name: "CalculatorEngineTests", dependencies: ["CalculatorEngine"])
    ]
)
