
package org.openautomaker.root.custom_dropwizard;

import java.nio.file.Path;

import org.openautomaker.root.security.User;

import io.dropwizard.auth.Authenticator;
import io.dropwizard.auth.basic.BasicCredentials;

/**
 *
 * @author alynch
 */
public class ExternalAuthenticatedAssetsBundle extends AuthenticatedAssetsBundle {

	private final Path externalStaticDir;

	public ExternalAuthenticatedAssetsBundle(Path externalStaticDir,
			String resourcePath, String uriPath,
			Authenticator<BasicCredentials, User> authenticator) {
		super(resourcePath, uriPath, authenticator);
		this.externalStaticDir = externalStaticDir;
	}

	@Override
	protected ExternalAuthenticatedAssetServlet createServlet() {
		return new ExternalAuthenticatedAssetServlet(externalStaticDir,
				resourcePath, uriPath, indexFile,
				authenticator);
	}

}
