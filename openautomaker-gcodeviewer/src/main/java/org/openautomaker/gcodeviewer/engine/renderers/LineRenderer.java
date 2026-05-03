package org.openautomaker.gcodeviewer.engine.renderers;

import static org.lwjgl.opengl.GL11.GL_LINES;
import static org.lwjgl.opengl.GL11.glDrawArrays;
import static org.lwjgl.opengl.GL20.glDisableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL30.glBindVertexArray;

import org.joml.Matrix4f;
import org.openautomaker.gcodeviewer.engine.RawEntity;
import org.openautomaker.gcodeviewer.engine.RenderParameters;
import org.openautomaker.gcodeviewer.entities.Camera;
import org.openautomaker.gcodeviewer.entities.Light;
import org.openautomaker.gcodeviewer.shaders.LineShader;

public class LineRenderer {

	private final LineShader shader;
	private Matrix4f projectionMatrix;

	public LineRenderer(LineShader shader, Matrix4f projectionMatrix) {
		this.shader = shader;
		this.projectionMatrix = projectionMatrix;
	}

	public void prepare(Camera camera,
			Light light,
			RenderParameters renderParameters) {
		MasterRenderer.checkErrors();
		shader.start();
		MasterRenderer.checkErrors();
		shader.setProjectionMatrix(projectionMatrix);
		MasterRenderer.checkErrors();
		shader.setViewMatrix(camera);
		MasterRenderer.checkErrors();
		shader.loadCompositeMatrix();
		MasterRenderer.checkErrors();
	}

	public void render(RawEntity rawEntity) {
		if (rawEntity != null) {
			bindRawModel(rawEntity);
			MasterRenderer.checkErrors();
			glDrawArrays(GL_LINES, 0, rawEntity.getVertexCount());
			MasterRenderer.checkErrors();
			unbindRawModel();
			MasterRenderer.checkErrors();
		}
	}

	public void finish() {
		shader.stop();
		MasterRenderer.checkErrors();
	}

	public void setProjectionMatrix(Matrix4f projectionMatrix) {
		this.projectionMatrix = projectionMatrix;
	}

	public void bindRawModel(RawEntity rawEntity) {
		glBindVertexArray(rawEntity.getVaoId());
		glEnableVertexAttribArray(0);
		glEnableVertexAttribArray(1);
	}

	public void unbindRawModel() {
		glDisableVertexAttribArray(0);
		glDisableVertexAttribArray(1);
		glBindVertexArray(0);
	}
}
