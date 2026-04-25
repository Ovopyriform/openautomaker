package celtech.services.modelLoader;

import java.io.File;
import java.util.List;

import org.openautomaker.base.services.ControllableService;
import org.openautomaker.ui.inject.model_loader.ModelLoaderTaskFactory;

import jakarta.inject.Inject;
import javafx.concurrent.Service;
import javafx.concurrent.Task;

public class ModelLoaderService extends Service<ModelLoadResults> implements
		ControllableService {

	private List<File> modelFilesToLoad;

	public final ModelLoaderTaskFactory modelLoaderTaskFactory;

	@Inject
	protected ModelLoaderService(
			ModelLoaderTaskFactory modelLoaderTaskFactory) {

		super();
		this.modelLoaderTaskFactory = modelLoaderTaskFactory;
	}

	public final void setModelFilesToLoad(List<File> modelFiles) {
		modelFilesToLoad = modelFiles;
	}

	@Override
	protected Task<ModelLoadResults> createTask() {
		return modelLoaderTaskFactory.create(modelFilesToLoad);
	}

	@Override
	public boolean cancelRun() {
		return cancel();
	}

}
