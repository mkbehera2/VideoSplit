package com.xiaopo.flying.videosplit;

import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.Matrix;

import com.xiaopo.flying.puzzlekit.Area;
import com.xiaopo.flying.puzzlekit.PuzzleLayout;
import com.xiaopo.flying.videosplit.gl.BufferUtil;
import com.xiaopo.flying.videosplit.gl.Shader;
import com.xiaopo.flying.videosplit.gl.ShaderParameter;
import com.xiaopo.flying.videosplit.gl.ShaderProgram;

import java.util.ArrayList;

/**
 * @author wupanjie
 */
public class SplitShaderProgram extends ShaderProgram {
  private static final int COORDS_PER_VERTEX = 2;
  private static final int VERTEX_STRIDE = COORDS_PER_VERTEX * FLOAT_SIZE;

  private static final String POSITION_ATTRIBUTE = "aPosition";
  private static final String MATRIX_UNIFORM = "uMatrix";
  private static final String TEXTURE_MATRIX_UNIFORM = "uTextureMatrix";
  private static final String TEXTURE_SAMPLER_UNIFORM = "uTextureSampler";

  private static final String VERTEX_SHADER_CODE = "uniform mat4 uMatrix;\n" +
      "uniform mat4 uTextureMatrix;\n" +
      "attribute vec2 aPosition;\n" +
      "varying vec2 vTextureCoord;\n" +
      "\n" +
      "void main(){\n" +
      "  vec4 pos = vec4(aPosition,0.0,1.0);\n" +
      "  gl_Position = uMatrix * pos;\n" +
      "  vTextureCoord = (uTextureMatrix * pos).xy;\n" +
      "}";

  private static final String FRAGMENT_SHADER_CODE = "#extension GL_OES_EGL_image_external : require\n" +
      "\n" +
      "precision mediump float;\n" +
      "varying vec2 vTextureCoord;\n" +
      "uniform samplerExternalOES uTextureSampler;\n" +
      "\n" +
      "float whiteBlack(vec3 vec3Color){\n" +
      "  float ave = (vec3Color.x + vec3Color.y+vec3Color.z)/3.0;\n" +
      "  if(ave>0.255)\n" +
      "    return 1.0;\n" +
      "  else\n" +
      "    return 0.0;\n" +
      "}\n" +
      "\n" +
      "void main(){\n" +
      "  vec4 textureColor = texture2D(uTextureSampler,vTextureCoord);\n" +
      "  float finalColor = whiteBlack(textureColor.xyz);\n" +
      "  gl_FragColor = vec4(finalColor,finalColor,finalColor,1.0);\n" +
      "}\n";

  private static final float vertexCoordinates[] = {
      0, 0, // Fill rectangle
      1, 0,
      0, 1,
      1, 1,
  };
  private int vertexBufferId;

  private float[] projectionMatrix = new float[16];
  private float[] viewMatrix = new float[16];

  private ArrayList<VideoPiece> videoPieces = new ArrayList<>();
  private PuzzleLayout puzzleLayout;

  private Shader shader;

  public void setPuzzleLayout(PuzzleLayout puzzleLayout) {
    this.puzzleLayout = puzzleLayout;
  }

  public void addPiece(final String path) {
    videoPieces.add(new VideoPiece(path));
  }

  @Override
  public void prepare() {
    shader = new Shader.Builder()
        .attachVertex(VERTEX_SHADER_CODE)
        .attachFragment(FRAGMENT_SHADER_CODE)
        .inflate(ShaderParameter.attribute(POSITION_ATTRIBUTE))
        .inflate(ShaderParameter.uniform(MATRIX_UNIFORM))
        .inflate(ShaderParameter.uniform(TEXTURE_MATRIX_UNIFORM))
        .inflate(ShaderParameter.uniform(TEXTURE_SAMPLER_UNIFORM))
        .assemble();

    vertexBufferId = uploadBuffer(BufferUtil.storeDataInBuffer(vertexCoordinates));

    final int size = videoPieces.size();
    int[] textureIds = new int[size];
    generateTextures(size, textureIds, 0);
    for (int i = 0; i < size; i++) {
      videoPieces.get(i).configOutput(textureIds[i]);
    }

    Matrix.setIdentityM(projectionMatrix, 0);
    Matrix.orthoM(projectionMatrix, 0, 0, getViewportWidth(), 0, getViewportHeight(), -1, 1);

    Matrix.setIdentityM(viewMatrix, 0);
    Matrix.translateM(viewMatrix, 0, 0, getViewportHeight(), 0);
    Matrix.scaleM(viewMatrix, 0, 1, -1, 1);
  }

  @Override
  public void run() {
    GLES20.glClearColor(1.0f, 0.0f, 0.0f, 0.0f);
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

    GLES20.glViewport(0, 0, getViewportWidth(), getViewportHeight());
    //set shader
    shader.activate();

    final int textureHandle = shader.getParameterHandle(TEXTURE_SAMPLER_UNIFORM);
    final int textureMatrixHandle = shader.getParameterHandle(TEXTURE_MATRIX_UNIFORM);
    final int matrixHandle = shader.getParameterHandle(MATRIX_UNIFORM);

    final int areaCount = puzzleLayout.getAreaCount();
    final int pieceCount = videoPieces.size();
    for (int i = 0; i < areaCount; i++) {
      Area area = puzzleLayout.getArea(i);
      VideoPiece piece = videoPieces.get(i % pieceCount);
      piece.setDisplayArea(area.getAreaRect());
      piece.setTexture(textureHandle, textureMatrixHandle);
      piece.setMatrix(matrixHandle, viewMatrix, projectionMatrix);
      drawElements();
    }
  }

  @Override
  public void release() {
    super.release();
    shader.release();
    for (VideoPiece videoPiece : videoPieces) {
      videoPiece.release();
    }
    videoPieces.clear();
  }

  private void drawElements() {
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBufferId);
    GLES20.glVertexAttribPointer(
        shader.getParameterHandle(POSITION_ATTRIBUTE),
        2,
        GLES20.GL_FLOAT,
        false,
        VERTEX_STRIDE,
        0);
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

    GLES20.glEnableVertexAttribArray(shader.getParameterHandle(POSITION_ATTRIBUTE));
    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, vertexCoordinates.length);
    GLES20.glDisableVertexAttribArray(shader.getParameterHandle(POSITION_ATTRIBUTE));
  }


  void updatePreviewTexture() {
    for (VideoPiece videoPiece : videoPieces) {
      videoPiece.getOutputTexture().updateTexImage();
    }
  }

  void setOnFrameAvailableListener(SurfaceTexture.OnFrameAvailableListener onFrameAvailableListener) {
    for (VideoPiece videoPiece : videoPieces) {
      videoPiece.getOutputTexture().setOnFrameAvailableListener(onFrameAvailableListener);
    }
  }

  void play() {
    for (VideoPiece videoPiece : videoPieces) {
      videoPiece.play();
    }
  }
}