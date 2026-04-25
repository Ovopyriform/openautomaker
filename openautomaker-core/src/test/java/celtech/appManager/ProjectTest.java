package celtech.appManager;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.openautomaker.base.configuration.Filament;
import org.openautomaker.base.configuration.datafileaccessors.FilamentContainer;
import org.openautomaker.base.configuration.fileRepresentation.SupportType;
import org.openautomaker.test_library.GuiceExtension;
import org.openautomaker.ui.inject.project.ProjectFactory;
import org.openautomaker.ui.project.robox.RoboxWriter;
import org.testfx.framework.junit5.Start;

import com.fasterxml.jackson.databind.ObjectMapper;

import celtech.TestUtils;
import celtech.modelcontrol.Groupable;
import celtech.modelcontrol.ModelContainer;
import celtech.modelcontrol.ModelGroup;
import celtech.modelcontrol.TranslateableTwoD;
import jakarta.inject.Inject;
import javafx.stage.Stage;
import javafx.util.Pair;

@ExtendWith({ GuiceExtension.class })
public class ProjectTest {

	private static final String GROUP_NAME = "group";
	private static final String MC3_ID = "mc3";

	private ObjectMapper objectMapper = new ObjectMapper();

	@Inject
	private FilamentContainer filamentContainer;

	@Inject
	ProjectFactory projectFactory;

	@Inject
	private ProjectManager projectManager;

	@Inject
	private RoboxWriter roboxWriter;

	@Inject
	private TestUtils testUtils;

	@Start
	public void start(Stage stage) {

	}

	@Test
	//TODO: This test causes an exception in JavaFX due to an NPE, but still passes.  Look at getting rid of the NPE.
	public void testSaveOneProject(@TempDir Path tempDir) throws Exception {
		String PROJECT_NAME = "TestA";
		int BRIM = 2;
		float FILL_DENSITY = 0.45f;
		SupportType PRINT_SUPPORT = SupportType.MATERIAL_2;
		String PRINT_JOB_ID = "PJ1";

		Filament FILAMENT_0 = filamentContainer.getFilamentByID("RBX-ABS-GR499");
		Filament FILAMENT_1 = filamentContainer.getFilamentByID("RBX-PLA-PP157");

		Project project = makeProject().getKey();
		project.setProjectName(PROJECT_NAME);
		project.getPrinterSettings().setBrimOverride(BRIM);
		project.getPrinterSettings().setFillDensityOverride(FILL_DENSITY);
		project.getPrinterSettings().setPrintSupportTypeOverride(PRINT_SUPPORT);
		project.setLastPrintJobID(PRINT_JOB_ID);
		project.setExtruder0Filament(FILAMENT_0);
		project.setExtruder1Filament(FILAMENT_1);

		Path tempFilePath = tempDir.resolve("testSaveOneProject.robox");
		roboxWriter.write(project, tempFilePath);

		Project newProject = projectManager.loadProject(tempFilePath);

		assertEquals(PROJECT_NAME, newProject.getProjectName());
		assertEquals(BRIM, newProject.getPrinterSettings().getBrimOverride());
		assertEquals(FILL_DENSITY, newProject.getPrinterSettings().getFillDensityOverride(), 1e-10);
		assertEquals(PRINT_SUPPORT, newProject.getPrinterSettings().getPrintSupportTypeOverride());
		assertEquals(FILAMENT_0, newProject.getExtruder0FilamentProperty().get());
		assertEquals(FILAMENT_1, newProject.getExtruder1FilamentProperty().get());
	}

	private Pair<Project, ModelGroup> makeProject() {
		ModelContainer mc1 = testUtils.makeModelContainer(true);
		ModelContainer mc2 = testUtils.makeModelContainer(true);
		ModelContainer mc3 = testUtils.makeModelContainer(true);
		mc3.setId("mc3");
		Project project = projectFactory.create();
		project.addModel(mc1);
		project.addModel(mc2);
		project.addModel(mc3);

		Set<TranslateableTwoD> toTranslate = new HashSet<>();
		toTranslate.add(mc2);
		project.translateModelsBy(toTranslate, 10, 20);

		Set<Groupable> modelContainers = new HashSet<>();
		modelContainers.add(mc1);
		modelContainers.add(mc2);
		ModelGroup group = project.group(modelContainers);
		group.setId(GROUP_NAME);
		return new Pair<>(project, group);
	}

	private Pair<Project, ModelGroup> makeProjectWithGroupOfGroups() {
		ModelContainer mc1 = testUtils.makeModelContainer(true);
		ModelContainer mc2 = testUtils.makeModelContainer(true);
		ModelContainer mc3 = testUtils.makeModelContainer(true);
		ModelContainer mc4 = testUtils.makeModelContainer(true);
		Project project = projectFactory.create();
		project.addModel(mc1);
		project.addModel(mc2);
		project.addModel(mc3);
		project.addModel(mc4);

		Set<Groupable> modelContainers = new HashSet<>();
		modelContainers.add(mc1);
		modelContainers.add(mc2);
		ModelContainer group = project.group(modelContainers);
		group.setId(GROUP_NAME);

		modelContainers = new HashSet<>();
		modelContainers.add(mc3);
		modelContainers.add(mc4);
		modelContainers.add(group);

		ModelGroup superGroup = project.group(modelContainers);

		return new Pair<>(project, superGroup);
	}

	//	@Test
	//	public void testSaveProjectWithGroup(@TempDir Path tempDir) throws IOException {
	//
	//		Pair<Project, ModelGroup> pair = makeProject();
	//		Project project = pair.getKey();
	//		Set<Integer> expectedIds = project.getTopLevelThings().stream().map(
	//				x -> x.getModelId()).collect(Collectors.toSet());
	//
	//		Path projectFilePath = tempDir.resolve("testSaveProjectWithGroup.robox");
	//		project.save(projectFilePath);
	//
	//		Project newProject = (Project) projectManager.loadProject(projectFilePath);
	//
	//		assertEquals(2, newProject.getTopLevelThings().size());
	//
	//		assertEquals(expectedIds,
	//				newProject.getTopLevelThings().stream().map(x -> x.getModelId()).collect(
	//						Collectors.toSet()));
	//	}
	//
	//	@Test
	//	public void testSaveProjectWithGroupOfGroupsThenLoadAndUngroup(@TempDir Path tempDir) throws IOException {
	//
	//		Pair<Project, ModelGroup> pair = makeProjectWithGroupOfGroups();
	//		Project project = pair.getKey();
	//		ModelGroup superGroup = pair.getValue();
	//		Set<Integer> expectedIds = superGroup.getChildModelContainers().stream().map(
	//				x -> x.getModelId()).collect(Collectors.toSet());
	//
	//		Path projectFilePath = tempDir.resolve("testSaveProjectWithGroupOfGroupsThenLoadAndUngroup.robox");
	//		project.save(projectFilePath);
	//
	//		Project newProject = (Project) projectManager.loadProject(projectFilePath);
	//
	//		assertEquals(1, newProject.getTopLevelThings().size());
	//
	//		Set<ProjectifiableThing> modelContainers = new HashSet<>(newProject.getTopLevelThings());
	//		newProject.ungroup((Set) modelContainers);
	//
	//		assertEquals(3, newProject.getTopLevelThings().size());
	//
	//		assertEquals(expectedIds,
	//				newProject.getTopLevelThings().stream().map(x -> x.getModelId()).collect(
	//						Collectors.toSet()));
	//
	//		Set<ModelGroup> modelGroups = newProject.getTopLevelThings().stream().filter(x -> x instanceof ModelGroup).map(x -> (ModelGroup) x).collect(Collectors.toSet());
	//
	//		assertEquals(1, modelGroups.size());
	//		ModelGroup modelGroup = modelGroups.iterator().next();
	//		assertEquals(2, modelGroup.getChildModelContainers().size());
	//
	//	}
	//
	//	@Test
	//	public void testSaveProjectWithGroupWithRotation(@TempDir Path tempDir) throws IOException {
	//
	//		double ROTATION = 20.1f;
	//
	//		Pair<Project, ModelGroup> pair = makeProject();
	//		Project project = pair.getKey();
	//		ModelGroup group = pair.getValue();
	//		group.setRotationLean(ROTATION);
	//
	//		Path projectFilePath = tempDir.resolve("testSaveProjectWithGroupWithRotation.robox");
	//
	//		project.save(projectFilePath);
	//
	//		Project newProject = (Project) projectManager.loadProject(projectFilePath);
	//
	//		Set<ModelGroup> modelGroups = newProject.getTopLevelThings().stream().filter(x -> x instanceof ModelGroup).map(x -> (ModelGroup) x).collect(Collectors.toSet());
	//
	//		assertEquals(1, modelGroups.size());
	//		ModelGroup modelGroup = modelGroups.iterator().next();
	//		assertEquals(ROTATION, modelGroup.getRotationLean(), 0.001);
	//	}
}
