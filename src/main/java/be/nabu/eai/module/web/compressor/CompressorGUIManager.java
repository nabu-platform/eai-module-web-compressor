package be.nabu.eai.module.web.compressor;

import java.io.IOException;
import java.util.List;

import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.managers.base.BaseJAXBGUIManager;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.libs.property.api.Property;
import be.nabu.libs.property.api.Value;

public class CompressorGUIManager extends BaseJAXBGUIManager<CompressorConfiguration, CompressorArtifact> {

	public CompressorGUIManager() {
		super("Compressor", CompressorArtifact.class, new CompressorManager(), CompressorConfiguration.class);
	}

	@Override
	protected List<Property<?>> getCreateProperties() {
		return null;
	}

	@Override
	protected CompressorArtifact newInstance(MainController controller, RepositoryEntry entry, Value<?>... values) throws IOException {
		return new CompressorArtifact(entry.getId(), entry.getContainer(), entry.getRepository());
	}
	
	@Override
	public String getCategory() {
		return "Web";
	}

}
