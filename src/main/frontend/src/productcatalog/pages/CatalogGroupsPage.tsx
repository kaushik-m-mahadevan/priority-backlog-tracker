import { useNavigate } from "react-router-dom";
import AppletGroupChooserPage from "../../components/AppletGroupChooserPage";
import { useProductCatalog } from "../ProductCatalogContext";

export default function CatalogGroupsPage() {
  const { catalogGroups, currentGroupId, loading, setCurrentCatalogGroup, createCatalogGroup } = useProductCatalog();
  const navigate = useNavigate();

  return (
    <AppletGroupChooserPage
      icon="🎨"
      groupNoun="catalog group"
      description="A catalog group is its own separate workspace, kept apart from any Order Tracker business on purpose — link it to a business from that business's Manage business page."
      groups={catalogGroups}
      currentGroupId={currentGroupId}
      loading={loading}
      onSelect={(id) => {
        setCurrentCatalogGroup(id);
        navigate("/productcatalog");
      }}
      onCreate={async (name) => {
        await createCatalogGroup(name);
        navigate("/productcatalog");
      }}
    />
  );
}
