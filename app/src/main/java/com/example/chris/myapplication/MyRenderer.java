package com.example.chris.myapplication;

import android.graphics.PointF;
import android.opengl.GLSurfaceView;
import android.util.Log;

import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Created by chris on 1/31/15.
 */
public class MyRenderer implements GLSurfaceView.Renderer {

    private static final String TAG = "MyActivity";

    private FloatBuffer triangleBuffer;
    private PointF surfaceSize;
    private MyActivity activity;

    public MyRenderer(MyActivity a) {
        activity = a;
        surfaceSize = new PointF();
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig eglConfig) {
        gl.glClearColor(0,0,0,1);
        float[] triangles = {
                // quad sideways 0
                   0, -20, 0,
                -100,   0, 0,
                 100,   0, 0,
                // bottom line 9
                0, 1, 0,
                960, 1, 0,
                // quad top 15
                0, 100, 0,
                -10,   0, 0,
                10,   0, 0,
                // stick box 24
                -100, -100, 0,
                 100, -100, 0,
                 100,  100, 0,
                -100,  100, 0,
                // stick pos 36
                0, 0, 0,
                // PPS meter 39
                0,  0, 0,
                10, 0, 0,
                10, 200, 0,
                0,  200, 0,

        };

        ByteBuffer mbb = ByteBuffer.allocateDirect(triangles.length * 4);
        mbb.order (ByteOrder.nativeOrder());
        triangleBuffer = mbb.asFloatBuffer();
        triangleBuffer.put(triangles);
        triangleBuffer.position(0);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int w, int h) {
        Log.d(TAG, "onSurfaceChanged " + w + "," + h);
        surfaceSize.set(w, h);

        gl.glViewport(0, 0, w, h);

        gl.glMatrixMode(GL10.GL_PROJECTION);
        gl.glLoadIdentity();
        gl.glOrthof(0, w, 0, h, -1, 1);
        gl.glMatrixMode(GL10.GL_MODELVIEW);

        gl.glDisable(GL10.GL_LIGHTING);
        gl.glDisable(GL10.GL_CULL_FACE);
        gl.glDisable(GL10.GL_DEPTH_BUFFER_BIT);
        gl.glDisable(GL10.GL_DEPTH_TEST);
        gl.glShadeModel(GL10.GL_SMOOTH);
        gl.glPointSize(15);
        gl.glEnable(GL10.GL_BLEND);
        gl.glBlendFunc(GL10.GL_SRC_ALPHA, GL10.GL_ONE_MINUS_SRC_ALPHA);
        gl.glEnable(GL10.GL_POINT_SMOOTH);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        gl.glClear(GL10.GL_COLOR_BUFFER_BIT);

        gl.glPushMatrix();

        gl.glLoadIdentity();

        gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);

        // roll
        gl.glColor4f(1.0f, 0.75f, 0.0f, 1);

        {
            gl.glPushMatrix();
            gl.glTranslatef(180, 0, 0);
            gl.glScalef(0.9f,0.9f,1);

            triangleBuffer.position(0);
            gl.glPushMatrix();
            gl.glTranslatef(225, 140, 0);
            gl.glRotatef(-activity.getRoll(), 0, 0, 1);
            gl.glVertexPointer(3, GL10.GL_FLOAT, 0, triangleBuffer);
            gl.glDrawArrays(GL10.GL_TRIANGLES, 0, 3);
            gl.glPopMatrix();

            // pitch
            gl.glColor4f(0.75f, 1.0f, 0.0f, 1);

            gl.glPushMatrix();
            gl.glTranslatef(225 + 240, 140, 0);
            gl.glRotatef(-activity.getPitch(), 0, 0, 1);
            gl.glVertexPointer(3, GL10.GL_FLOAT, 0, triangleBuffer);
            gl.glDrawArrays(GL10.GL_TRIANGLES, 0, 3);
            gl.glPopMatrix();

            // yaw
            gl.glColor4f(0.0f, 0.75f, 1.0f, 1);

            triangleBuffer.position(15);
            gl.glPushMatrix();
            gl.glTranslatef(225 + 240 + 240, 140, 0);
            gl.glRotatef(-activity.getYaw(), 0, 0, 1);
            gl.glVertexPointer(3, GL10.GL_FLOAT, 0, triangleBuffer);
            gl.glDrawArrays(GL10.GL_TRIANGLE_FAN, 0, 3);
            gl.glPopMatrix();

            gl.glPopMatrix();
        }

        // stick boxes
        {
            gl.glLineWidth(4);
            gl.glPushMatrix();
            gl.glTranslatef(85,75,0);
            gl.glScalef(0.5f,0.5f,1);

            if ( (activity.getFlags() & 0x2) == 0x2 )
                gl.glColor4f(0.75f, 0.0f, 0.0f, 1);
            else
                gl.glColor4f(0.0f, 0.75f, 0.0f, 1);

            triangleBuffer.position(24);
            gl.glPushMatrix();
                gl.glTranslatef(0, 0, 0);
                gl.glVertexPointer(3, GL10.GL_FLOAT, 0, triangleBuffer);
                gl.glDrawArrays(GL10.GL_LINE_LOOP, 0, 4);
            gl.glPopMatrix();

            gl.glPushMatrix();
                gl.glTranslatef(220, 0, 0);
                gl.glVertexPointer(3, GL10.GL_FLOAT, 0, triangleBuffer);
                gl.glDrawArrays(GL10.GL_LINE_LOOP, 0, 4);
            gl.glPopMatrix();

            // stick dots

            gl.glColor4f(0, 1, 0, 1);
            triangleBuffer.position(36);
            gl.glVertexPointer(3, GL10.GL_FLOAT, 0, triangleBuffer);
            gl.glPushMatrix();
                gl.glTranslatef( (activity.getRadioData().yaw - 127)/1.27f, (activity.getRadioData().throttle - 127)/1.27f, 0 );
                gl.glDrawArrays(GL10.GL_POINTS, 0, 1);
            gl.glPopMatrix();
            gl.glPushMatrix();
                gl.glTranslatef( 220 + (activity.getRadioData().roll - 127)/1.27f, (activity.getRadioData().pitch - 127)/1.27f, 0 );
                gl.glDrawArrays(GL10.GL_POINTS, 0, 1);
            gl.glPopMatrix();

            gl.glPopMatrix();
        }

        // PPS meter
        {
            gl.glLineWidth(2);
            gl.glPushMatrix();
            gl.glTranslatef(12.5f,25,0);

            int meterHeight = activity.getPPS() * 2;
            if ( meterHeight > 200 )
                meterHeight = 200;
            triangleBuffer.position(46);
            triangleBuffer.put(meterHeight);
            triangleBuffer.position(49);
            triangleBuffer.put(meterHeight);

            float green = (meterHeight/100.0f);
            gl.glColor4f(1-green,green,0.2f,1);

            triangleBuffer.position(39);
            gl.glVertexPointer(3, GL10.GL_FLOAT, 0, triangleBuffer);
            gl.glDrawArrays(GL10.GL_TRIANGLE_FAN, 0, 4);

            triangleBuffer.position(46);
            triangleBuffer.put(200);
            triangleBuffer.position(49);
            triangleBuffer.put(200);

            gl.glColor4f(0.75f,0.75f*green,0.75f*green,1);
            triangleBuffer.position(39);
            gl.glVertexPointer(3, GL10.GL_FLOAT, 0, triangleBuffer);
            gl.glDrawArrays(GL10.GL_LINE_LOOP, 0, 4);
            gl.glPopMatrix();
        }

        // bottom line
        gl.glLineWidth(2);
        triangleBuffer.position(9);
        gl.glColor4f(1,1,1,1);
        gl.glVertexPointer(3, GL10.GL_FLOAT, 0, triangleBuffer);
        gl.glDrawArrays(GL10.GL_LINES, 0, 2);

        gl.glDisableClientState(GL10.GL_VERTEX_ARRAY);

        gl.glPopMatrix();

    }
}
