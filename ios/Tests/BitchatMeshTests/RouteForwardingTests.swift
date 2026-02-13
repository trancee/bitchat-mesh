import XCTest
@testable import BitchatMesh

final class RouteForwardingTests: XCTestCase {
    private func hop(_ hex: String) -> Data {
        return Data(hexString: hex) ?? Data()
    }

    func testNextHopDataForIntermediateRoute() {
        let selfData = hop("0102030405060708")
        let nextHop = hop("1112131415161718")
        let recipient = hop("2122232425262728")
        let route = [selfData, nextHop]

        let resolved = BLEService.nextHopData(route: route, selfData: selfData, recipientID: recipient)

        XCTAssertEqual(resolved, nextHop)
    }

    func testNextHopDataForLastIntermediateUsesRecipient() {
        let selfData = hop("0102030405060708")
        let recipient = hop("2122232425262728")
        let route = [selfData]

        let resolved = BLEService.nextHopData(route: route, selfData: selfData, recipientID: recipient)

        XCTAssertEqual(resolved, recipient)
    }

    func testNextHopDataReturnsNilWhenNotInRoute() {
        let selfData = hop("0102030405060708")
        let route = [hop("1112131415161718")]
        let recipient = hop("2122232425262728")

        let resolved = BLEService.nextHopData(route: route, selfData: selfData, recipientID: recipient)

        XCTAssertNil(resolved)
    }

    func testNextHopDataReturnsNilWhenRecipientMissing() {
        let selfData = hop("0102030405060708")
        let route = [selfData]

        let resolved = BLEService.nextHopData(route: route, selfData: selfData, recipientID: nil)

        XCTAssertNil(resolved)
    }
}
