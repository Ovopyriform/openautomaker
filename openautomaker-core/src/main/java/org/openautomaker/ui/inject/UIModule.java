package org.openautomaker.ui.inject;

import org.openautomaker.ui.component.menu_panel.user_preference.AdvancedModePreferenceController;
import org.openautomaker.ui.component.menu_panel.user_preference.CurrencySymbolPreferenceController;
import org.openautomaker.ui.component.menu_panel.user_preference.CustomPrinterHeadPreferenceController;
import org.openautomaker.ui.component.menu_panel.user_preference.CustomPrinterTypePreferenceController;
import org.openautomaker.ui.component.menu_panel.user_preference.FloatingPointPreference;
import org.openautomaker.ui.component.menu_panel.user_preference.LanguagePreferenceController;
import org.openautomaker.ui.component.menu_panel.user_preference.LogLevelPreferenceController;
import org.openautomaker.ui.component.menu_panel.user_preference.SlicerTypePreferenceController;
import org.openautomaker.ui.component.menu_panel.user_preference.TickBoxPreference;
import org.openautomaker.ui.inject.controller.AdvancedModePreferenceControllerFactory;
import org.openautomaker.ui.inject.controller.CurrencySymbolPreferenceControllerFactory;
import org.openautomaker.ui.inject.controller.CustomPrinterHeadPreferenceControllerFactory;
import org.openautomaker.ui.inject.controller.CustomPrinterTypePreferenceControllerFactory;
import org.openautomaker.ui.inject.controller.FloatingPointPreferenceFactory;
import org.openautomaker.ui.inject.controller.LanguagePreferenceControllerFactory;
import org.openautomaker.ui.inject.controller.LogLevelPreferenceControllerFactory;
import org.openautomaker.ui.inject.controller.SlicerTypePreferenceControllerFactory;
import org.openautomaker.ui.inject.controller.TickBoxPreferenceFactory;
import org.openautomaker.ui.inject.model.ModelContainerFactory;
import org.openautomaker.ui.inject.model.ModelGroupFactory;
import org.openautomaker.ui.inject.model_loader.ModelLoaderTaskFactory;
import org.openautomaker.ui.inject.project.ProjectFactory;
import org.openautomaker.ui.inject.project.ProjectGUIRulesFactory;
import org.openautomaker.ui.inject.project.ProjectGUIStateFactory;
import org.openautomaker.ui.inject.project.ProjectSelectionFactory;
import org.openautomaker.ui.inject.undo.CutCommandFactory;
import org.openautomaker.ui.inject.undo.UndoableProjectFactory;
import org.openautomaker.ui.inject.utils.settings_generation.ProfileDetailsGeneratorFactory;
import org.openautomaker.ui.inject.visualisation.DimensionLineFactory;
import org.openautomaker.ui.inject.visualisation.DimensionLineManagerFactory;
import org.openautomaker.ui.inject.visualisation.ThreeDViewManagerFactory;
import org.openautomaker.ui.project.adapter.ProjectBridgeFactory;
import org.openautomaker.ui.project.loader.ObjModelLoader;
import org.openautomaker.ui.project.loader.StlModelLoader;
import org.openautomaker.ui.project.loader.SvgModelLoader;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.FactoryModuleBuilder;

import java.util.List;

import org.openautomaker.project.api.IModelLoader;
import org.openautomaker.project.api.IProjectFactory;

import celtech.appManager.GCodeGeneratorManager;
import celtech.appManager.GCodeGeneratorManagerFactory;
import celtech.appManager.Project;
import celtech.appManager.undo.CutCommand;
import celtech.appManager.undo.UndoableProject;
import org.openautomaker.ui.ProjectGUIRules;
import org.openautomaker.ui.ProjectGUIState;
import celtech.coreUI.visualisation.DimensionLine;
import celtech.coreUI.visualisation.DimensionLineManager;
import celtech.coreUI.visualisation.ProjectSelection;
import celtech.coreUI.visualisation.ThreeDViewManager;
import celtech.modelcontrol.ModelContainer;
import celtech.modelcontrol.ModelGroup;
import celtech.services.modelLoader.ModelLoaderTask;
import celtech.utils.settingsgeneration.ProfileDetailsGenerator;

public class UIModule extends AbstractModule {

	protected void overrideBindings() {

	}

	@Override
	public void configure() {

		install(new FactoryModuleBuilder()
				.implement(GCodeGeneratorManager.class, GCodeGeneratorManager.class)
				.build(GCodeGeneratorManagerFactory.class));

		install(new FactoryModuleBuilder()
				.implement(CutCommand.class, CutCommand.class)
				.build(CutCommandFactory.class));

		install(new FactoryModuleBuilder()
				.implement(UndoableProject.class, UndoableProject.class)
				.build(UndoableProjectFactory.class));

		install(new FactoryModuleBuilder()
				.implement(Project.class, Project.class)
				.build(ProjectFactory.class));

		install(new FactoryModuleBuilder()
				.implement(ModelContainer.class, ModelContainer.class)
				.build(ModelContainerFactory.class));

		install(new FactoryModuleBuilder()
				.implement(ModelGroup.class, ModelGroup.class)
				.build(ModelGroupFactory.class));

		install(new FactoryModuleBuilder()
				.implement(ProjectSelection.class, ProjectSelection.class)
				.build(ProjectSelectionFactory.class));

		install(new FactoryModuleBuilder()
				.implement(ProjectGUIState.class, ProjectGUIState.class)
				.build(ProjectGUIStateFactory.class));

		install(new FactoryModuleBuilder()
				.implement(ProjectGUIRules.class, ProjectGUIRules.class)
				.build(ProjectGUIRulesFactory.class));

		install(new FactoryModuleBuilder()
				.implement(ModelLoaderTask.class, ModelLoaderTask.class)
				.build(ModelLoaderTaskFactory.class));

		install(new FactoryModuleBuilder()
				.implement(ThreeDViewManager.class, ThreeDViewManager.class)
				.build(ThreeDViewManagerFactory.class));

		install(new FactoryModuleBuilder()
				.implement(DimensionLine.class, DimensionLine.class)
				.build(DimensionLineFactory.class));

		install(new FactoryModuleBuilder()
				.implement(AdvancedModePreferenceController.class, AdvancedModePreferenceController.class)
				.build(AdvancedModePreferenceControllerFactory.class));

		install(new FactoryModuleBuilder()
				.implement(SlicerTypePreferenceController.class, SlicerTypePreferenceController.class)
				.build(SlicerTypePreferenceControllerFactory.class));

		install(new FactoryModuleBuilder()
				.implement(LanguagePreferenceController.class, LanguagePreferenceController.class)
				.build(LanguagePreferenceControllerFactory.class));

		install(new FactoryModuleBuilder()
				.implement(TickBoxPreference.class, TickBoxPreference.class)
				.build(TickBoxPreferenceFactory.class));

		install(new FactoryModuleBuilder()
				.implement(LogLevelPreferenceController.class, LogLevelPreferenceController.class)
				.build(LogLevelPreferenceControllerFactory.class));

		install(new FactoryModuleBuilder()
				.implement(CustomPrinterTypePreferenceController.class, CustomPrinterTypePreferenceController.class)
				.build(CustomPrinterTypePreferenceControllerFactory.class));

		install(new FactoryModuleBuilder()
				.implement(CustomPrinterHeadPreferenceController.class, CustomPrinterHeadPreferenceController.class)
				.build(CustomPrinterHeadPreferenceControllerFactory.class));

		install(new FactoryModuleBuilder()
				.implement(CurrencySymbolPreferenceController.class, CurrencySymbolPreferenceController.class)
				.build(CurrencySymbolPreferenceControllerFactory.class));

		install(new FactoryModuleBuilder()
				.implement(FloatingPointPreference.class, FloatingPointPreference.class)
				.build(FloatingPointPreferenceFactory.class));

		//TODO: Teems to be fixed.  Keep an eye on this for now
		install(new FactoryModuleBuilder()
				.implement(ProfileDetailsGenerator.class, ProfileDetailsGenerator.class)
				.build(ProfileDetailsGeneratorFactory.class));

		install(new FactoryModuleBuilder()
				.implement(DimensionLineManager.class, DimensionLineManager.class)
				.build(DimensionLineManagerFactory.class));

		bind(IProjectFactory.class).to(ProjectBridgeFactory.class);
	}

	@Provides
	@Singleton
	List<IModelLoader> provideModelLoaders(StlModelLoader stlLoader, ObjModelLoader objLoader, SvgModelLoader svgLoader) {
		return List.of(stlLoader, objLoader, svgLoader);
	}
}
