package org.openautomaker.gcodeviewer.engine.renderers;

import static org.lwjgl.opengl.GL11.GL_LINES;
import static org.lwjgl.opengl.GL11.glDrawArrays;
import static org.lwjgl.opengl.GL20.glDisableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL30.glBindVertexArray;

import java.util.List;

import org.joml.Matrix4f;
import org.openautomaker.gcodeviewer.engine.RawModel;
import org.openautomaker.gcodeviewer.entities.LineEntity;
import org.openautomaker.gcodeviewer.shaders.LineModelShader;
import org.openautomaker.gcodeviewer.utils.MatrixUtils;

/**
 *
 * @author George Salter
 */
public class LineModelRenderer {

	private final LineModelShader shader;

	LineModelRenderer(LineModelShader shader, Matrix4f projectionMatrix) {
		this.shader = shader;
		loadProjectionMatrix(projectionMatrix);
	}

	public final void loadProjectionMatrix(Matrix4f projectionMatrix) {
		shader.start();
		shader.loadProjectionMatrix(projectionMatrix);
		shader.stop();
	}

	public void render(List<LineEntity> lineEntities) {
		RawModel lineModel = lineEntities.get(0).getLineModel();
		prepareRawModel(lineModel);
		lineEntities.forEach(lineEntity -> {
			prepareInstance(lineEntity);
			glDrawArrays(GL_LINES, 0, 2);
		});
		unbindRawModel();
	}

	public void prepareRawModel(RawModel model) {
		glBindVertexArray(model.getVaoId());
		glEnableVertexAttribArray(0);
	}

	public void unbindRawModel() {
		glDisableVertexAttribArray(0);
		glBindVertexArray(0);
	}

	public void prepareInstance(LineEntity lineEntity) {
		Matrix4f transformationMatrix = MatrixUtils.createTransformationMatrixForLine(
				lineEntity.getPosition(),
				lineEntity.getRotationX(),
				lineEntity.getRotationY(),
				lineEntity.getRotationZ(),
				lineEntity.getLength());
		shader.loadTransformationMatrix(transformationMatrix);
		shader.loadColour(lineEntity.getColour());
	}
}
