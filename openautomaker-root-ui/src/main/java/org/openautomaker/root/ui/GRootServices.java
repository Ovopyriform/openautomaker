package org.openautomaker.root.ui;

import org.openautomaker.environment.I18N;

import com.google.inject.Singleton;

import jakarta.inject.Inject;

@Singleton
public final class GRootServices {

	private static GRootServices instance;
	private final I18N i18n;

	@Inject
	public GRootServices(I18N i18n) {
		this.i18n = i18n;
		instance = this;
	}

	public static I18N getI18N() {
		return instance.i18n;
	}
}
