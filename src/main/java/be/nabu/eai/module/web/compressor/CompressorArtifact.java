/*
* Copyright (C) 2017 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package be.nabu.eai.module.web.compressor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import be.nabu.eai.module.web.application.WebApplication;
import be.nabu.eai.module.web.application.WebFragment;
import be.nabu.eai.module.web.application.WebFragmentPriority;
import be.nabu.eai.repository.api.Repository;
import be.nabu.eai.repository.artifacts.jaxb.JAXBArtifact;
import be.nabu.libs.authentication.api.Permission;
import be.nabu.libs.cache.impl.ByteSerializer;
import be.nabu.libs.cache.impl.StringSerializer;
import be.nabu.libs.events.api.EventSubscription;
import be.nabu.libs.http.api.HTTPResponse;
import be.nabu.libs.resources.api.ResourceContainer;

public class CompressorArtifact extends JAXBArtifact<CompressorConfiguration> implements WebFragment {

	private Map<String, EventSubscription<?, ?>> subscriptions = new HashMap<String, EventSubscription<?, ?>>();
	
	public CompressorArtifact(String id, ResourceContainer<?> directory, Repository repository) {
		super(id, directory, repository, "compressor.xml", CompressorConfiguration.class);
	}

	@Override
	public void start(WebApplication artifact, String path) throws IOException {
		if (isStarted(artifact, path)) {
			stop(artifact, path);
		}
		// allow you to disable
		if (getConfig().isEnabled()) {
			EventSubscription<HTTPResponse, HTTPResponse> subscription = artifact.getDispatcher().subscribe(HTTPResponse.class, new CompressorListener(this));
			subscriptions.put(getKey(artifact, path), subscription);
			if (getConfig().getCacheProvider() != null) {
				getConfig().getCacheProvider().create(getId(), 1024l*1024*100, 1024l*1024*10, new StringSerializer(), new ByteSerializer(), null, null);
			}
		}
	}

	@Override
	public void stop(WebApplication artifact, String path) {
		String key = getKey(artifact, path);
		if (subscriptions.containsKey(key)) {
			synchronized(subscriptions) {
				if (subscriptions.containsKey(key)) {
					subscriptions.get(key).unsubscribe();
					subscriptions.remove(key);
				}
			}
		}
		if (getConfig().getCacheProvider() != null) {
			// @2022-03-22: due to a bug clustered caches were never reset correctly on shutdown. this bug has been fixed but we wanted to keep this behavior, in the future we might reenable it with a checkbox
//			try {
//				getConfig().getCacheProvider().get(getId()).clear();
//			}
//			catch (IOException e) {
//				// ignore
//			}
		}
	}
	
	private String getKey(WebApplication artifact, String path) {
		return artifact.getId() + ":" + path;
	}

	@Override
	public List<Permission> getPermissions(WebApplication artifact, String path) {
		return new ArrayList<Permission>();
	}

	@Override
	public boolean isStarted(WebApplication artifact, String path) {
		return subscriptions.containsKey(getKey(artifact, path));
	}

	@Override
	public WebFragmentPriority getPriority() {
		return WebFragmentPriority.LOW;
	}
	
}
