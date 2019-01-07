package com.asha.vrlib.objects;

import android.content.Context;
import android.graphics.PointF;
import android.graphics.RectF;

import com.asha.vrlib.MD360Program;
import com.asha.vrlib.MDVRLibrary;
import com.asha.vrlib.strategy.projection.TinyPlanetProjection;
import com.google.vrtoolkit.cardboard.sensors.internal.Matrix3x3d;
import com.google.vrtoolkit.cardboard.sensors.internal.Vector3d;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

public class MDTinyPlanet extends MDAbsObject3D {

    private static final String TAG = "MDTinyPlanet";

    private float mPrevRatio;

    private RectF mSize;

    private TinyPlanetProjection.PlaneScaleCalculator mCalculator;

    private MDTinyPlanet(TinyPlanetProjection.PlaneScaleCalculator calculator, RectF size) {
        this.mCalculator = calculator;
        this.mSize = size;
    }

    public MDTinyPlanet(TinyPlanetProjection.PlaneScaleCalculator calculator) {
        this(calculator,new RectF(0,0,1.0f,1.0f));
    }

    public MDTinyPlanet(RectF size) {
        this(new TinyPlanetProjection.PlaneScaleCalculator(MDVRLibrary.PROJECTION_MODE_PLANE_FULL,new RectF(0,0,100,100)),size);
    }

    @Override
    protected void executeLoad(Context context) {
        generateMesh(this);
    }

    @Override
    public void uploadVerticesBufferIfNeed(MD360Program program, int index) {
        if (super.getVerticesBuffer(index) == null){
            return;
        }

        // update the texture only if the index == 0
        if (index == 0){
            float ratio = mCalculator.getTextureRatio();
            if (ratio != mPrevRatio) {

                float[] vertexs = generateVertex();

                // initialize vertex byte buffer for shape coordinates
                ByteBuffer bb = ByteBuffer.allocateDirect(
                        // (# of coordinate values * 4 bytes per float)
                        vertexs.length * 4);
                bb.order(ByteOrder.nativeOrder());
                FloatBuffer buffer = bb.asFloatBuffer();
                buffer.put(vertexs);
                buffer.position(0);

                setVerticesBuffer(0,buffer);
                setVerticesBuffer(1,buffer);

                mPrevRatio = ratio;
            }
        }

        super.uploadVerticesBufferIfNeed(program, index);
    }

    private float[] generateVertex(){
        int z = 0;
        mCalculator.calculate();
        mPrevRatio = mCalculator.getTextureRatio();
        float width = mCalculator.getTextureWidth() * mSize.width();
        float height = mCalculator.getTextureHeight() * mSize.height();

        float[] vertexs = new float[getNumPoint() * 3];
        int rows = getNumRow();
        int columns = getNumColumn();
        float R = 1f/(float) rows;
        float S = 1f/(float) columns;
        short r, s;

        int v = 0;
        for(r = 0; r < rows + 1; r++) {
            for(s = 0; s < columns + 1; s++) {
                vertexs[v++] = (s * S - 0.5f) * width;
                vertexs[v++] = (r * R - 0.5f) * height;
                vertexs[v++] = z;
            }
        }

        return vertexs;
    }

    private float[] generateTexcoords(){
        float[] texcoords = new float[getNumPoint() * 2];

        int rows = getNumRow();
        int columns = getNumColumn();
        float R = 1f/(float) rows;
        float S = 1f/(float) columns;
        short r, s;

        int t = 0;
        for(r = 0; r < rows + 1; r++) {
            for(s = 0; s < columns + 1; s++) {
                final float PI = (float) Math.PI;

                float tex_x = s*S;
                float tex_y = r*R;

                Vector3d rot = new Vector3d();
                rot.setZero();

                float scale = 10.0f;
                float height = 1920.0f;
                float width = 3840.0f;

                float px = 0.0f;
                float py = 0.0f;

                Matrix3x3d matRot = getRotation(rot);
                PointF offset = new PointF(px, py);
                PointF pnt = new PointF();

                pnt.x = (tex_x - 0.5f - offset.x) * scale;
                pnt.y = (tex_y - 0.5f - offset.y) * scale * height/width;

                // Project to Sphere
                float x2y2 = pnt.x * pnt.x + pnt.y * pnt.y;
                Vector3d sphere_pnt_ = new Vector3d(2.0f * pnt.x / (x2y2 + 1.0f), 2.0f * pnt.y / (x2y2 + 1.0f), (x2y2 - 1.0f) / (x2y2 + 1.0f));


                Vector3d sphere_pnt = new Vector3d();
                multiplyVecAndMat(sphere_pnt_, matRot, sphere_pnt);

                // Convert to Spherical Coordinates
                float dist = (float)Math.sqrt(Math.pow(sphere_pnt.x, 2.0) + Math.pow(sphere_pnt.y, 2.0) + Math.pow(sphere_pnt.z, 2.0));
                float lon = (float)Math.atan2(sphere_pnt.y, sphere_pnt.x);
                float lat = (float)Math.acos(-1.0 * sphere_pnt.z / dist);

                lon = lon / PI * 0.5f + 0.5f;
                lat = lat / PI;

                texcoords[t++] = lon;
                texcoords[t++] = lat;
            }
        }

        return texcoords;
    }

    private static Matrix3x3d getRotation(Vector3d r)
    {
        double cx = Math.cos(Math.toRadians(r.x));
        double sx = Math.sin(Math.toRadians(r.x));
        double cy = Math.cos(Math.toRadians(r.y));
        double sy = Math.sin(Math.toRadians(r.y));
        double cz = Math.cos(Math.toRadians(r.z));
        double sz = Math.sin(Math.toRadians(r.z));

        double m1,m2,m3,m4,m5,m6,m7,m8,m9;

        m1= cy * cz;
        m2= cx * sz + sx * sy * cz;
        m3= sx * sz - cx * sy * cz;
        m4=-cy * sz;
        m5= cx * cz - sx * sy * sz;
        m6= sx * cz + cx * sy * sz;
        m7= sy;
        m8=-sx * cy;
        m9= cx * cy;

        Matrix3x3d ret = new Matrix3x3d();
        ret.set(m1,m2,m3,m4,m5,m6,m7,m8,m9);

        return ret;
    }

    private static void multiplyVecAndMat(final Vector3d v, final Matrix3x3d a, final Vector3d result) {
        final double x = a.m[0] * v.x + a.m[3] * v.y + a.m[6] * v.z;
        final double y = a.m[1] * v.x + a.m[4] * v.y + a.m[7] * v.z;
        final double z = a.m[2] * v.x + a.m[5] * v.y + a.m[8] * v.z;
        result.x = x;
        result.y = y;
        result.z = z;
    }

    private void generateMesh(MDAbsObject3D object3D){
        int rows = getNumRow();
        int columns = getNumColumn();
        short r, s;

        float[] vertexs = generateVertex();
        float[] texcoords = generateTexcoords();
        short[] indices = new short[getNumPoint() * 6];


        int counter = 0;
        int sectorsPlusOne = columns + 1;
        for(r = 0; r < rows; r++){
            for(s = 0; s < columns; s++) {
                short k0 = (short) ((r) * sectorsPlusOne + (s+1));  // (c)
                short k1 = (short) ((r+1) * sectorsPlusOne + (s));    //(b)
                short k2 = (short) (r * sectorsPlusOne + s);       //(a);
                short k3 = (short) ((r) * sectorsPlusOne + (s+1));  // (c)
                short k4 = (short) ((r+1) * sectorsPlusOne + (s+1));  // (d)
                short k5 = (short) ((r+1) * sectorsPlusOne + (s));    //(b)

                indices[counter++] = k0;
                indices[counter++] = k1;
                indices[counter++] = k2;
                indices[counter++] = k3;
                indices[counter++] = k4;
                indices[counter++] = k5;
            }
        }

        // initialize vertex byte buffer for shape coordinates
        ByteBuffer bb = ByteBuffer.allocateDirect(
                // (# of coordinate values * 4 bytes per float)
                vertexs.length * 4);
        bb.order(ByteOrder.nativeOrder());
        FloatBuffer vertexBuffer = bb.asFloatBuffer();
        vertexBuffer.put(vertexs);
        vertexBuffer.position(0);

        // initialize vertex byte buffer for shape coordinates
        ByteBuffer cc = ByteBuffer.allocateDirect(
                texcoords.length * 4);
        cc.order(ByteOrder.nativeOrder());
        FloatBuffer texBuffer = cc.asFloatBuffer();
        texBuffer.put(texcoords);
        texBuffer.position(0);

        // initialize byte buffer for the draw list
        ByteBuffer dlb = ByteBuffer.allocateDirect(
                // (# of coordinate values * 2 bytes per short)
                indices.length * 2);
        dlb.order(ByteOrder.nativeOrder());
        ShortBuffer indexBuffer = dlb.asShortBuffer();
        indexBuffer.put(indices);
        indexBuffer.position(0);

        object3D.setIndicesBuffer(indexBuffer);
        object3D.setTexCoordinateBuffer(0,texBuffer);
        object3D.setTexCoordinateBuffer(1,texBuffer);
        object3D.setVerticesBuffer(0,vertexBuffer);
        object3D.setVerticesBuffer(1,vertexBuffer);
        object3D.setNumIndices(indices.length);
    }

    private int getNumPoint(){
        return (getNumRow() + 1) * (getNumColumn() + 1);
    }

    private int getNumRow(){
        return 200;
    }

    private int getNumColumn(){
        return 100;
    }
}
