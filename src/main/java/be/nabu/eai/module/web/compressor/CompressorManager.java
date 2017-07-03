package be.nabu.eai.module.web.compressor;

import be.nabu.eai.repository.api.Repository;
import be.nabu.eai.repository.managers.base.JAXBArtifactManager;
import be.nabu.libs.resources.api.ResourceContainer;

public class CompressorManager extends JAXBArtifactManager<CompressorConfiguration, CompressorArtifact> {

	public CompressorManager() {
		super(CompressorArtifact.class);
	}

	@Override
	protected CompressorArtifact newInstance(String id, ResourceContainer<?> container, Repository repository) {
		return new CompressorArtifact(id, container, repository);
	}

}
