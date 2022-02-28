package be.ntmn.widget;


/*
 * libcommon
 * utility/helper classes for myself
 *
 * Copyright (c) 2014-2018 saki t_saki@serenegiant.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
*/

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.opengl.Matrix;

import be.ntmn.IDrawer2D;
import be.ntmn.ITexture;
import be.ntmn.TextureOffscreen;

import static be.ntmn.ShaderConst.*;

/**
 * 描画領域全面にテクスチャを2D描画するためのヘルパークラス
 */
public class GLDrawer2D1 implements IDrawer2dES21 {
//	private static final boolean DEBUG = false; // FIXME set false on release
//	private static final String TAG = "GLDrawer2D";

   private static final float[] VERTICES = { 1.0f, 1.0f, -1.0f, 1.0f, 1.0f, -1.0f, -1.0f, -1.0f };
   private   float[] VERTICES2 = { 2.0f, 2.0f, -2.0f, 2.0f, 2.0f, -2.0f, -2.0f, -2.0f };
    private static final float[] VERTICES15 = { 1.5f, 1.5f, -1.5f, 1.5f, 1.5f, -1.5f, -1.5f, -1.5f };
    private static final float[] TEXCOORD = { 1.0f, 1.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f };
    private static final float[] TEXCOORD1 = { 0.0f, 0.0f, 1.0f, 0.0f,0.0f, 1.0f , 1.0f, 1.0f };
    private static final int FLOAT_SZ = Float.SIZE / 8;

    private final int VERTEX_NUM;
    private final int VERTEX_SZ;
    private final FloatBuffer pVertex;
    private final FloatBuffer pTexCoord;
    private final int mTexTarget;
    private int hProgram;
    int maPositionLoc;
    int maTextureCoordLoc;
    int muMVPMatrixLoc;
    int mTexture2D;
    int muTexMatrixLoc;
    private final float[] mMvpMatrix = new float[16];
    private static final String TAG = "GLDrawer2D1";

    /**
     * コンストラクタ
     * GLコンテキスト/EGLレンダリングコンテキストが有効な状態で呼ばないとダメ
     * @param isOES 外部テクスチャ(GL_TEXTURE_EXTERNAL_OES)を使う場合はtrue。
     * 				通常の2Dテキスチャならfalse
     */
    public GLDrawer2D1(final boolean isOES) {
        this(VERTICES, TEXCOORD, isOES);
//        this(VERTICES, MyApp.deviceName.contains("DL")? TEXCOORD1:TEXCOORD, isOES);
    }

    /**
     * コンストラクタ
     * GLコンテキスト/EGLレンダリングコンテキストが有効な状態で呼ばないとダメ
     * @param vertices 頂点座標, floatを8個 = (x,y) x 4ペア
     * @param texcoord テクスチャ座標, floatを8個 = (s,t) x 4ペア
     * @param isOES 外部テクスチャ(GL_TEXTURE_EXTERNAL_OES)を使う場合はtrue。
     * 				通常の2Dテキスチャならfalse
     */
    public GLDrawer2D1(final float[] vertices,
                      final float[] texcoord, final boolean isOES) {

        VERTEX_NUM = Math.min(
                vertices != null ? vertices.length : 0,
                texcoord != null ? texcoord.length : 0) / 2;
        VERTEX_SZ = VERTEX_NUM * 2;

        mTexTarget = isOES ? GL_TEXTURE_EXTERNAL_OES : GL_TEXTURE_2D;
        pVertex = ByteBuffer.allocateDirect(VERTEX_SZ * FLOAT_SZ)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        pVertex.put(vertices);
        pVertex.flip();
        pTexCoord = ByteBuffer.allocateDirect(VERTEX_SZ * FLOAT_SZ)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        pTexCoord.put(texcoord);
        pTexCoord.flip();

        if (isOES) {
            hProgram = GLHelper1.loadShader(VERTEX_SHADER1, FRAGMENT_SHADER_SIMPLE_OES1);
        } else {
            hProgram = GLHelper1.loadShader(VERTEX_SHADER1, FRAGMENT_SHADER_SIMPLE1);
        }
        // モデルビュー変換行列を初期化
        Matrix.setIdentityM(mMvpMatrix, 0);
        init();
    }
    public void setVertices(float scale){
        VERTICES2[0]=scale*VERTICES[0];
        VERTICES2[1]=scale*VERTICES[1];
        VERTICES2[2]=scale*VERTICES[2];
        VERTICES2[3]=scale*VERTICES[3];
        VERTICES2[4]=scale*VERTICES[4];
        VERTICES2[5]=scale*VERTICES[5];
        VERTICES2[6]=scale*VERTICES[6];
        VERTICES2[7]=scale*VERTICES[7];


        pVertex.put(VERTICES2);
        pVertex.flip();
        executeVertices();
    }
    public void executeVertices(){
        GLES20.glVertexAttribPointer(maPositionLoc,
                2, GLES20.GL_FLOAT, false, VERTEX_SZ, pVertex);
        GLES20.glEnableVertexAttribArray(maPositionLoc);
    }
    // 頂点シェーダー
    /**
     * モデルビュー変換行列とテクスチャ変換行列適用するだけの頂点シェーダー
     */
    public static final String VERTEX_SHADER1 = SHADER_VERSION +
            "uniform mat4 uMVPMatrix;\n" +				// モデルビュー変換行列
            "uniform mat4 uTexMatrix;\n" +				// テクスチャ変換行列
            "attribute highp vec4 aPosition;\n" +		// 頂点座標
            "attribute highp vec4 aTextureCoord;\n" +	// テクスチャ情報
            "varying highp vec2 vTextureCoord;\n" +		// フラグメントシェーダーへ引き渡すテクスチャ座標
            "void main() {\n" +
            "    gl_Position = uMVPMatrix * aPosition;\n" +
            "    vTextureCoord = (uTexMatrix * aTextureCoord).xy;\n" +
            "}\n";

    // フラグメントシェーダー
    public static final String FRAGMENT_SHADER_SIMPLE_OES1
            = SHADER_VERSION
            + HEADER_OES
            + "precision mediump float;\n"
            + "uniform samplerExternalOES sTexture;\n"
            + "uniform sampler2D sTexture2D;\n"
            + "varying highp vec2 vTextureCoord;\n"
            + "void main() {\n"
            + "  lowp vec4 TextureColor = texture2D(sTexture, vTextureCoord);\n"
            + "  lowp vec4 TextureColor2D = texture2D(sTexture2D, vTextureCoord);\n"
            + "  gl_FragColor=TextureColor*(1.0-TextureColor2D.a)+TextureColor2D*TextureColor2D.a;\n"
            + "}";

    public static final String FRAGMENT_SHADER_SIMPLE1
            = SHADER_VERSION
            + HEADER_2D
            + "precision mediump float;\n"
            + "uniform sampler2D sTexture;\n"
            + "varying highp vec2 vTextureCoord;\n"
            + "void main() {\n"
            + "  gl_FragColor = texture2D(sTexture, vTextureCoord);\n"
            + "}";

    //
    // Simple fragment shader for use with "normal" 2D textures.
    private static final String FRAGMENT_SHADER_BASE1 = SHADER_VERSION +
            "%s" +
            "precision mediump float;\n" +
            "varying vec2 vTextureCoord;\n" +
            "uniform %s sTexture;\n" +
            "void main() {\n" +
            "    gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
            "}\n";

    /**
     * 破棄処理。GLコンテキスト/EGLレンダリングコンテキスト内で呼び出さないとダメ
     */
    @Override
    public void release() {
        if (hProgram >= 0) {
            GLES20.glDeleteProgram(hProgram);
        }
        hProgram = -1;
    }

    /**
     * 外部テクスチャを使うかどうか
     * @return
     */
    public boolean isOES() {
        return mTexTarget == GL_TEXTURE_EXTERNAL_OES;
    }

    /**
     * モデルビュー変換行列を取得(内部配列を直接返すので変更時は要注意)
     * @return
     */
    @Override
    public float[] getMvpMatrix() {
        return mMvpMatrix;
    }

    /**
     * モデルビュー変換行列に行列を割り当てる
     * @param matrix 領域チェックしていないのでoffsetから16個以上必須
     * @param offset
     * @return
     */
    @Override
    public IDrawer2D setMvpMatrix(final float[] matrix, final int offset) {
        System.arraycopy(matrix, offset, mMvpMatrix, 0, 16);
        return this;
    }

    /**
     * モデルビュー変換行列のコピーを取得
     * @param matrix 領域チェックしていないのでoffsetから16個以上必須
     * @param offset
     */
    @Override
    public void getMvpMatrix(final float[] matrix, final int offset) {
        System.arraycopy(mMvpMatrix, 0, matrix, offset, 16);
    }

    /**
     * 指定したテクスチャを指定したテクスチャ変換行列を使って描画領域全面に描画するためのヘルパーメソッド
     * このクラスインスタンスのモデルビュー変換行列が設定されていればそれも適用された状態で描画する
     * @param texId texture ID
     * @param tex_matrix テクスチャ変換行列、nullならば以前に適用したものが再利用される。
     * 					領域チェックしていないのでoffsetから16個以上確保しておくこと
     */
    @Override
    public synchronized void draw(final int texId,
                                  final float[] tex_matrix, final int offset) {

//		if (DEBUG) Log.v(TAG, "draw");
        if (hProgram < 0) return;
        GLES20.glUseProgram(hProgram);
        if (tex_matrix != null) {
            // テクスチャ変換行列が指定されている時
            GLES20.glUniformMatrix4fv(muTexMatrixLoc, 1, false, tex_matrix, offset);
        }
        // モデルビュー変換行列をセット
        GLES20.glUniformMatrix4fv(muMVPMatrixLoc, 1, false, mMvpMatrix, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(mTexTarget, texId);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, VERTEX_NUM);
        GLES20.glBindTexture(mTexTarget, 0);
        GLES20.glUseProgram(0);
    }



    @Override
    public synchronized void draw(final int[] texIds,
                                  final float[] tex_matrix, final int offset,Bitmap bitmap) {

//		if (DEBUG) Log.v(TAG, "draw");
        if (hProgram < 0) return;
      //  final Bitmap bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888);
        // get a canvas to paint over the bitmap
     //   final Canvas canvas = new Canvas(bitmap);
           // canvas.drawARGB(255, 255, 255, 255);
      //      canvas.drawARGB(0,0,0,0);
      //  bitcanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
       // canvas.drawBitmap(mCursorYellow, bitmap.getWidth() / 2.0f, bitmap.getHeight() / 2.0f , photoPaint);
      //  canvas.save(Canvas.ALL_SAVE_FLAG);
        GLES20.glUseProgram(hProgram);
        if (tex_matrix != null) {
            // テクスチャ変換行列が指定されている時
            GLES20.glUniformMatrix4fv(muTexMatrixLoc, 1, false, tex_matrix, offset);
        }
        // モデルビュー変換行列をセット
        GLES20.glUniformMatrix4fv(muMVPMatrixLoc, 1, false, mMvpMatrix, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(mTexTarget, texIds[0]);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, VERTEX_NUM);
        GLES20.glBindTexture(mTexTarget, 0);
        if(texIds[1]==3){
                GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
            GLES20.glUniform1i(mTexture2D,2);
           // Log.e(TAG, "draw:texIds[1]==3:"+texIds[1]);
        }else{
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
            GLES20.glUniform1i(mTexture2D,1);
           // Log.e(TAG, "draw:texIds[1]==else:"+texIds[1]);
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texIds[1]);
        // ByteBuffer buf = ByteBuffer.allocate(bitmap.getWidth() * bitmap.getHeight() * 4);
        // bitmap.copyPixelsToBuffer(buf);
        // GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_RGBA,512,512,0,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,buf);

        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);

        GLES20.glUseProgram(0);
    }

    /**
     * Textureオブジェクトを描画するためのヘルパーメソッド
     * Textureオブジェクトで管理しているテクスチャ名とテクスチャ座標変換行列を使って描画する
     * @param texture
     */
    @Override
    public void draw(final ITexture texture) {
        draw(texture.getTexture(), texture.getTexMatrix(), 0);
    }

    /**
     * TextureOffscreenオブジェクトを描画するためのヘルパーメソッド
     * @param offscreen
     */
    @Override
    public void draw(final TextureOffscreen offscreen) {
        draw(offscreen.getTexture(), offscreen.getTexMatrix(), 0);
    }

    /**
     * テクスチャ名生成のヘルパーメソッド
     * GLHelper#initTexを呼び出すだけ
     * @return texture ID
     */
    public int initTex() {
        return GLHelper1.initTex(mTexTarget, GLES20.GL_LINEAR);
    }
    public int[] initTexes(int[] para) {
        return GLHelper1.initTexes(para);
    }
    /**
     * テクスチャ名破棄のヘルパーメソッド
     * GLHelper.deleteTexを呼び出すだけ
     * @param hTex
     */
    public void deleteTex(final int hTex) {
        GLHelper1.deleteTex(hTex);
    }
    public void deleteTexes(final int[] hTex) {
        GLHelper1.deleteTex(hTex);
    }

    /**
     * 頂点シェーダー・フラグメントシェーダーを変更する
     * GLコンテキスト/EGLレンダリングコンテキスト内で呼び出さないとダメ
     * glUseProgramが呼ばれた状態で返る
     * @param vs 頂点シェーダー文字列
     * @param fs フラグメントシェーダー文字列
     */
    public synchronized void updateShader(final String vs, final String fs) {
        release();
        hProgram = GLHelper1.loadShader(vs, fs);
        init();
    }

    /**
     * フラグメントシェーダーを変更する
     * GLコンテキスト/EGLレンダリングコンテキスト内で呼び出さないとダメ
     * glUseProgramが呼ばれた状態で返る
     * @param fs フラグメントシェーダー文字列
     */
    public void updateShader(final String fs) {
        updateShader(VERTEX_SHADER, fs);
    }

    /**
     * 頂点シェーダー・フラグメントシェーダーをデフォルトに戻す
     */
    public void resetShader() {
        release();
        if (isOES()) {
            hProgram = GLHelper1.loadShader(VERTEX_SHADER, FRAGMENT_SHADER_SIMPLE_OES1);
        } else {
            hProgram = GLHelper1.loadShader(VERTEX_SHADER, FRAGMENT_SHADER_SIMPLE1);
        }
        init();
    }

    /**
     * アトリビュート変数のロケーションを取得
     * glUseProgramが呼ばれた状態で返る
     * @param name
     * @return
     */
    @Override
    public int glGetAttribLocation(final String name) {
        GLES20.glUseProgram(hProgram);
        return GLES20.glGetAttribLocation(hProgram, name);
    }

    /**
     * ユニフォーム変数のロケーションを取得
     * glUseProgramが呼ばれた状態で返る
     * @param name
     * @return
     */
    @Override
    public int glGetUniformLocation(final String name) {
        GLES20.glUseProgram(hProgram);
        return GLES20.glGetUniformLocation(hProgram, name);
    }

    /**
     * glUseProgramが呼ばれた状態で返る
     */
    @Override
    public void glUseProgram() {
        GLES20.glUseProgram(hProgram);
    }

    /**
     * シェーダープログラム変更時の初期化処理
     * glUseProgramが呼ばれた状態で返る
     */
    private void init() {
        GLES20.glUseProgram(hProgram);
        maPositionLoc = GLES20.glGetAttribLocation(hProgram, "aPosition");
        maTextureCoordLoc = GLES20.glGetAttribLocation(hProgram, "aTextureCoord");
        muMVPMatrixLoc = GLES20.glGetUniformLocation(hProgram, "uMVPMatrix");
        muTexMatrixLoc = GLES20.glGetUniformLocation(hProgram, "uTexMatrix");
        mTexture2D= GLES20.glGetUniformLocation(hProgram, "sTexture2D");
        //
        GLES20.glUniformMatrix4fv(muMVPMatrixLoc,
                1, false, mMvpMatrix, 0);
        GLES20.glUniformMatrix4fv(muTexMatrixLoc,
                1, false, mMvpMatrix, 0);
        GLES20.glVertexAttribPointer(maPositionLoc,
                2, GLES20.GL_FLOAT, false, VERTEX_SZ, pVertex);
        GLES20.glVertexAttribPointer(maTextureCoordLoc,
                2, GLES20.GL_FLOAT, false, VERTEX_SZ, pTexCoord);
        GLES20.glEnableVertexAttribArray(maPositionLoc);
        GLES20.glEnableVertexAttribArray(maTextureCoordLoc);
        GLES20.glUniform1i(mTexture2D,1);
       // GLES20.glUniform1i(mTexture2D,2);
    }
}
