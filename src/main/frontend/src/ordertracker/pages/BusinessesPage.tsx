import { useNavigate } from "react-router-dom";
import AppletGroupChooserPage from "../../components/AppletGroupChooserPage";
import { useBusiness } from "../BusinessContext";

export default function BusinessesPage() {
  const { businesses, currentGroupId, loading, setCurrentBusiness, createBusiness } = useBusiness();
  const navigate = useNavigate();

  return (
    <AppletGroupChooserPage
      icon="✂"
      groupNoun="business"
      description="Every business here is its own separate Order Tracker workspace — customers, orders, and settings never cross between them."
      groups={businesses}
      currentGroupId={currentGroupId}
      loading={loading}
      onSelect={(id) => {
        setCurrentBusiness(id);
        navigate("/ordertracker/orders");
      }}
      onCreate={async (name) => {
        await createBusiness(name);
        navigate("/ordertracker/orders");
      }}
    />
  );
}
