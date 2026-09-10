import { Link } from "react-router-dom";
import EmptyLeaf from "./EmptyLeaf";

/** Shown on any group-scoped page when the user has no group selected. */
export default function NoGroupNotice() {
  return (
    <div className="card" style={{ maxWidth: 480, margin: "40px auto", textAlign: "center" }}>
      <EmptyLeaf message="Nothing to show yet — your work lives inside a group." />
      <Link to="/groups">Create or join a group →</Link>
    </div>
  );
}
