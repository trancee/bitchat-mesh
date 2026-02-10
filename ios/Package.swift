// swift-tools-version: 5.9

import PackageDescription

let package = Package(
    name: "BitchatMesh",
    defaultLocalization: "en",
    platforms: [
        .iOS(.v16),
        .macOS(.v13)
    ],
    products: [
        .library(
            name: "BitchatMesh",
            targets: ["BitchatMesh"]
        )
    ],
    dependencies: [
        .package(path: "localPackages/BitLogger")
    ],
    targets: [
        .target(
            name: "BitchatMesh",
            dependencies: [
                .product(name: "BitLogger", package: "BitLogger")
            ],
            path: "bitchat-mesh/Sources/BitchatMesh"
        ),
        .testTarget(
            name: "BitchatMeshTests",
            dependencies: ["BitchatMesh"],
            path: "Tests/BitchatMeshTests"
        )
    ]
)
