import XCTest
@testable import BitchatMesh

final class GeohashTests: XCTestCase {
    func testGeohashEncodeDecodeBoundsContainPoint() {
        let lat = 37.7749
        let lon = -122.4194
        let precision = 8

        let geohash = Geohash.encode(latitude: lat, longitude: lon, precision: precision)
        XCTAssertEqual(geohash.count, precision)
        XCTAssertTrue(Geohash.isValidBuildingGeohash(geohash))

        let bounds = Geohash.decodeBounds(geohash)
        XCTAssertGreaterThanOrEqual(lat, bounds.latMin)
        XCTAssertLessThanOrEqual(lat, bounds.latMax)
        XCTAssertGreaterThanOrEqual(lon, bounds.lonMin)
        XCTAssertLessThanOrEqual(lon, bounds.lonMax)
    }

    func testGeohashNeighborsAreValidAndDistinct() {
        let geohash = Geohash.encode(latitude: 37.7749, longitude: -122.4194, precision: 8)
        let neighbors = Geohash.neighbors(of: geohash)

        XCTAssertEqual(neighbors.count, 8)
        XCTAssertFalse(neighbors.contains(geohash))
        XCTAssertEqual(Set(neighbors).count, 8)
        XCTAssertTrue(neighbors.allSatisfy { $0.count == 8 })
        XCTAssertTrue(neighbors.allSatisfy { Geohash.isValidBuildingGeohash($0) })
    }

    func testGeohashValidationRejectsInvalidLengthAndCharacters() {
        XCTAssertFalse(Geohash.isValidBuildingGeohash(""))
        XCTAssertFalse(Geohash.isValidBuildingGeohash("abc"))
        XCTAssertFalse(Geohash.isValidBuildingGeohash("abc123"))

        XCTAssertFalse(Geohash.isValidBuildingGeohash("abcd123!"))
        XCTAssertFalse(Geohash.isValidBuildingGeohash("abcd123o"))
        XCTAssertFalse(Geohash.isValidBuildingGeohash("abcd123i"))
    }

    func testGeohashEncodeReturnsEmptyWhenPrecisionIsNonPositive() {
        XCTAssertEqual(Geohash.encode(latitude: 0.0, longitude: 0.0, precision: 0), "")
        XCTAssertEqual(Geohash.encode(latitude: 0.0, longitude: 0.0, precision: -3), "")
    }

    func testGeohashDecodeCenterReencodesToSameCell() {
        let precision = 8
        let geohash = Geohash.encode(latitude: 37.7749, longitude: -122.4194, precision: precision)
        let center = Geohash.decodeCenter(geohash)
        let reencoded = Geohash.encode(latitude: center.lat, longitude: center.lon, precision: precision)

        XCTAssertEqual(reencoded, geohash)
    }

    func testGeohashNeighborsNearPoleSkipOutOfBoundsCells() {
        let geohash = Geohash.encode(latitude: 89.9999, longitude: 0.0, precision: 8)
        let neighbors = Geohash.neighbors(of: geohash)

        XCTAssertGreaterThan(neighbors.count, 0)
        XCTAssertLessThan(neighbors.count, 8)
        XCTAssertEqual(Set(neighbors).count, neighbors.count)
        XCTAssertTrue(neighbors.allSatisfy { $0.count == 8 })
        XCTAssertTrue(neighbors.allSatisfy { Geohash.isValidBuildingGeohash($0) })
    }
}
