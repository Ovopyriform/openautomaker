package org.openautomaker.root.security;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.dropwizard.auth.UnauthorizedHandler;

public class RootAPIUnauthorisedHandler implements UnauthorizedHandler
{
	private static final String authenticationType = "RootAuth";
	@Override
	public Response buildResponse(String prefix, String realm)
	{
		return Response.status(Response.Status.UNAUTHORIZED)
				.header(HttpHeaders.WWW_AUTHENTICATE, authenticationType)
				.type(MediaType.TEXT_PLAIN_TYPE)
				.entity("Credentials are required to access this resource.")
				.build();
	}

	public static String getAuthenticationString()
	{
		return authenticationType;
	}
}
