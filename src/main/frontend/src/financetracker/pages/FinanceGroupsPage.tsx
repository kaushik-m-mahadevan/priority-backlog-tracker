import { useNavigate } from "react-router-dom";
import AppletGroupChooserPage from "../../components/AppletGroupChooserPage";
import { useFinanceGroup } from "../FinanceGroupContext";

export default function FinanceGroupsPage() {
  const { financeGroups, currentGroupId, loading, setCurrentFinanceGroup, createFinanceGroup } = useFinanceGroup();
  const navigate = useNavigate();

  return (
    <AppletGroupChooserPage
      icon="💰"
      groupNoun="finance group"
      description="A finance group is its own separate workspace, kept apart from any Order Tracker business on purpose — link it to a business from that business's Manage business page."
      groups={financeGroups}
      currentGroupId={currentGroupId}
      loading={loading}
      onSelect={(id) => {
        setCurrentFinanceGroup(id);
        navigate("/financetracker");
      }}
      onCreate={async (name) => {
        await createFinanceGroup(name);
        navigate("/financetracker");
      }}
    />
  );
}
