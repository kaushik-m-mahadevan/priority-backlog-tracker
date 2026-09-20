import { useState } from "react";
import { orderTrackerApi } from "../../api";
import type { OrderView } from "../../types";

/** Extracted from OrderDetailPage.tsx (fdup-1) — fully self-contained via props, no
 *  closure over the parent's state. */
export function ShippingCard({ groupId, order, onUpdated }: { groupId: string; order: OrderView; onUpdated: (o: OrderView) => void }) {
  const [showForm, setShowForm] = useState(false);
  const [origin, setOrigin] = useState("");
  const [destination, setDestination] = useState("");
  const [carrier, setCarrier] = useState("");
  const [tracking, setTracking] = useState("");

  const addStop = async () => {
    if (!origin.trim() || !destination.trim()) return;
    const stops = order.shipmentPlan.map((s) => ({
      stopOrder: s.stopOrder, type: s.type, originLocationCode: s.originLocationCode,
      destinationLocationCode: s.destinationLocationCode, laneId: s.laneId, estimatedCost: s.estimatedCost,
      estimatedTimeHours: s.estimatedTimeHours, carrier: s.carrier, trackingNumber: s.trackingNumber,
      triggerDate: s.triggerDate,
    }));
    stops.push({
      stopOrder: stops.length + 1, type: "FINAL_DELIVERY", originLocationCode: origin, destinationLocationCode: destination,
      laneId: null, estimatedCost: 0, estimatedTimeHours: 0, carrier: carrier || null, trackingNumber: tracking || null,
      triggerDate: null,
    });
    onUpdated(await orderTrackerApi.setShipmentPlan(groupId, order.id, stops));
    setShowForm(false);
    setOrigin("");
    setDestination("");
    setCarrier("");
    setTracking("");
  };

  return (
    <div className="card" style={{ marginTop: 16 }}>
      <h2>Shipping</h2>
      {order.shipmentPlan.length === 0 ? (
        <p className="empty">No shipment stops yet.</p>
      ) : (
        order.shipmentPlan.map((s, i) => (
          <div className="row" key={i}>
            <span className="k">
              {s.type} — {s.originLocationCode} → {s.destinationLocationCode}
              {s.carrier ? ` (${s.carrier})` : ""}
            </span>
            <span className="v">
              {s.deliveredConfirmed ? "Delivered" : s.shippedDate ? "Shipped" : (
                <button
                  type="button"
                  aria-label={`Mark stop ${i + 1} (${s.originLocationCode} to ${s.destinationLocationCode}) shipped`}
                  onClick={async () =>
                    onUpdated(await orderTrackerApi.markShipmentStop(groupId, order.id, i, { shippedDate: new Date().toISOString() }))}>
                  Mark shipped
                </button>
              )}
              {!s.deliveredConfirmed && s.shippedDate && (
                <button
                  type="button"
                  style={{ marginLeft: 6 }}
                  aria-label={`Mark stop ${i + 1} (${s.originLocationCode} to ${s.destinationLocationCode}) delivered`}
                  onClick={async () =>
                    onUpdated(await orderTrackerApi.markShipmentStop(groupId, order.id, i, { deliveredConfirmed: true }))}>
                  Mark delivered
                </button>
              )}
            </span>
          </div>
        ))
      )}
      {showForm ? (
        <div className="toolbar" style={{ marginTop: 10, flexWrap: "wrap" }}>
          <input aria-label="Origin code" placeholder="Origin code" value={origin} onChange={(e) => setOrigin(e.target.value)} />
          <input aria-label="Destination code" placeholder="Destination code" value={destination} onChange={(e) => setDestination(e.target.value)} />
          <input aria-label="Carrier" placeholder="Carrier" value={carrier} onChange={(e) => setCarrier(e.target.value)} />
          <input aria-label="Tracking number" placeholder="Tracking number" value={tracking} onChange={(e) => setTracking(e.target.value)} />
          <button className="primary" type="button" onClick={addStop}>
            Add stop
          </button>
          <button type="button" onClick={() => setShowForm(false)}>
            Cancel
          </button>
        </div>
      ) : (
        <button type="button" style={{ marginTop: 10 }} onClick={() => setShowForm(true)}>
          + Add shipment stop
        </button>
      )}
    </div>
  );
}
