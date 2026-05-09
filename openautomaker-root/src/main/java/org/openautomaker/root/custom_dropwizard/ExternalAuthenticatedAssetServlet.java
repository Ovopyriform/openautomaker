
package org.openautomaker.root.custom_dropwizard;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.root.security.User;

import io.dropwizard.auth.Authenticator;
import io.dropwizard.auth.basic.BasicCredentials;

/**
 *
 * @author alynch
 */
public class ExternalAuthenticatedAssetServlet extends AuthenticatedAssetServlet {

	private static final long serialVersionUID = -635680929587985903L;

	private static final Logger LOGGER = LogManager.getLogger();

	private final Path externalStaticDir;

	public ExternalAuthenticatedAssetServlet(Path externalStaticDir,
			String resourcePath,
			String uriPath,
			String indexFile,
			Authenticator<BasicCredentials, User> authenticator) {

		super(resourcePath, uriPath, indexFile, StandardCharsets.UTF_8, authenticator);
		this.externalStaticDir = externalStaticDir;
		LOGGER.info("Using external static dir at " + externalStaticDir);
	}

	@Override
	protected byte[] readResource(URL requestedResourceURL) throws IOException {
		String absPath = requestedResourceURL.getPath();

		int assetsIx = absPath.lastIndexOf("assets");
		String relPath = absPath.substring(assetsIx + 7);
		Path fileLocation = externalStaticDir.resolve(relPath);
		if (Files.isReadable(fileLocation))
		{
			System.out.println("Get external resource: " + absPath);
			return Files.readAllBytes(fileLocation);
		} else
		{
			return super.readResource(requestedResourceURL);
		}
	}

}
