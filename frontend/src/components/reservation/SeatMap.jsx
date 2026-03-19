const sectionColors = {
  VIP: { bg: '#fff3cd', border: '#ffc107', text: '#664d03' },
  R: { bg: '#d2e5e8', border: '#377381', text: '#244f59' },
  S: { bg: '#d1e7dd', border: '#198754', text: '#0f5132' },
  A: { bg: '#e2e3e5', border: '#6c757d', text: '#41464b' },
}

export default function SeatMap({ seats, selectedSeatId, onSelect }) {
  return (
    <div>
      <div className="text-center mb-3 p-2 bg-dark text-white rounded">STAGE</div>
      <div className="row g-3">
        {seats.map((seat) => {
          const colors = sectionColors[seat.section] || sectionColors.A
          const isSelected = selectedSeatId === seat.seatId
          const soldOut = seat.availableCount <= 0

          return (
            <div key={seat.seatId} className="col-md-6 col-lg-3">
              <button
                className="card w-100 text-start border-2"
                style={{
                  backgroundColor: isSelected ? colors.border : colors.bg,
                  borderColor: colors.border,
                  color: isSelected ? '#fff' : colors.text,
                  opacity: soldOut ? 0.5 : 1,
                  cursor: soldOut ? 'not-allowed' : 'pointer',
                }}
                onClick={() => !soldOut && onSelect(seat)}
                disabled={soldOut}
              >
                <div className="card-body p-3">
                  <div className="fw-bold mb-1">{seat.section}석</div>
                  <div className="small">
                    잔여 {seat.availableCount} / {seat.totalCount}
                  </div>
                  <div className="fw-semibold mt-1">
                    {seat.price.toLocaleString()}원
                  </div>
                  {soldOut && <div className="mt-1 fw-bold">매진</div>}
                </div>
              </button>
            </div>
          )
        })}
      </div>
    </div>
  )
}
