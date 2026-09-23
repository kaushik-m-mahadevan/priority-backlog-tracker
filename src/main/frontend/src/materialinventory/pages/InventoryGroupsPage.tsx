import { useNavigate } from "react-router-dom";
import AppletGroupChooserPage from "../../components/AppletGroupChooserPage";
import { useMaterialInventory } from "../MaterialInventoryContext";

export default function InventoryGroupsPage() {
  const { inventoryGroups, currentGroupId, loading, setCurrentInventoryGroup, createInventoryGroup } = useMaterialInventory();
  const navigate = useNavigate();

  return (
    <AppletGroupChooserPage
      icon="🧶"
      groupNoun="inventory group"
      description="An inventory group is its own separate workspace, kept apart from any Order Tracker business on purpose — link it to a business from that business's Manage business page."
      groups={inventoryGroups}
      currentGroupId={currentGroupId}
      loading={loading}
      onSelect={(id) => {
        setCurrentInventoryGroup(id);
        navigate("/materialinventory");
      }}
      onCreate={async (name) => {
        await createInventoryGroup(name);
        navigate("/materialinventory");
      }}
    />
  );
}
