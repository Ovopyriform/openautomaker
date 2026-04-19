package org.openautomaker.project.api;

/**
 * Creates blank {@link IProject} instances.
 * Implemented by openautomaker-core (adapting {@code ModelContainerProjectFactory}).
 */
public interface IProjectFactory {

	IProject create();
}
