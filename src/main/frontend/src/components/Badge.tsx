export function StatusBadge({ status }: { status: string }) {
  const label = status === "IN_PROGRESS" ? "In progress" : "Backlog";
  return <span className={`badge s-${status}`}>{label}</span>;
}

export function TerminalBadge({ status }: { status: string }) {
  return <span className={`badge t-${status}`}>{status[0] + status.slice(1).toLowerCase()}</span>;
}
