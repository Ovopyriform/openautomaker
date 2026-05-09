package org.openautomaker.root.security;

import java.security.Principal;

/**
 *
 * @author Ian
 */
public class User implements Principal
{

	private final String name;

	public User(String name)
	{
		this.name = name;
	}

	@Override
	public String getName()
	{
		return name;
	}

	public int getId()
	{
		return (int) (Math.random() * 100);
	}
}
