package com.asha.vrlib.strategy.projection;

import android.content.Context;
import android.graphics.RectF;
import android.opengl.Matrix;

import com.asha.vrlib.MD360Director;
import com.asha.vrlib.MD360DirectorFactory;
import com.asha.vrlib.MDVRLibrary;
import com.asha.vrlib.model.MDMainPluginBuilder;
import com.asha.vrlib.model.MDPosition;
import com.asha.vrlib.objects.MDAbsObject3D;
import com.asha.vrlib.objects.MDObject3DHelper;
import com.asha.vrlib.objects.MDTinyPlanet;
import com.asha.vrlib.plugins.MDAbsPlugin;
import com.asha.vrlib.plugins.MDPanoramaPlugin;


public class TinyPlanetProjection extends AbsProjectionStrategy {

    private MDTinyPlanet object3D;

    private TinyPlanetProjection.PlaneScaleCalculator planeScaleCalculator;

    private static final MDPosition position = MDPosition.newInstance().setZ(-2f);

    private TinyPlanetProjection(TinyPlanetProjection.PlaneScaleCalculator calculator) {
        planeScaleCalculator = calculator;
    }

    @Override
    public void turnOnInGL(Context context) {
        object3D = new MDTinyPlanet(planeScaleCalculator);
        MDObject3DHelper.loadObj(context, object3D);
    }

    @Override
    public void turnOffInGL(Context context) {

    }

    @Override
    public boolean isSupport(Context context) {
        return true;
    }

    @Override
    public MDAbsObject3D getObject3D() {
        return object3D;
    }

    @Override
    public MDPosition getModelPosition() {
        return position;
    }

    @Override
    public MDAbsPlugin buildMainPlugin(MDMainPluginBuilder builder) {
        return new MDPanoramaPlugin(builder);
    }

    @Override
    protected MD360DirectorFactory hijackDirectorFactory() {
        return new TinyPlanetProjection.OrthogonalDirectorFactory();
    }

    public static TinyPlanetProjection create(int scaleType, RectF textureSize){
        return new TinyPlanetProjection(new TinyPlanetProjection.PlaneScaleCalculator(scaleType,textureSize));
    }

    public static class PlaneScaleCalculator{

        private static final float sBaseValue = 1.0f;

        private RectF mTextureSize;

        private float mViewportRatio;

        private int mScaleType;

        private float mViewportWidth = sBaseValue;

        private float mViewportHeight = sBaseValue;

        private float mTextureWidth = sBaseValue;

        private float mTextureHeight = sBaseValue;

        public PlaneScaleCalculator(int scaleType, RectF textureSize) {
            this.mScaleType = scaleType;
            this.mTextureSize = textureSize;
        }

        public float getTextureRatio(){
            return mTextureSize.width() / mTextureSize.height();
        }

        public void setViewportRatio(float viewportRatio){
            this.mViewportRatio = viewportRatio;
        }

        public void calculate(){
            float viewportRatio = mViewportRatio;
            float textureRatio = getTextureRatio();

            switch (this.mScaleType){
                case MDVRLibrary.PROJECTION_MODE_PLANE_FULL:
                    // fullscreen
                    mViewportWidth = mViewportHeight = mTextureWidth = mTextureHeight = sBaseValue;
                    break;
                case MDVRLibrary.PROJECTION_MODE_PLANE_CROP:
                    if (textureRatio  > viewportRatio){
                        /**
                         * crop width of texture
                         *
                         * texture
                         * ----------------------
                         * |    |          |    |
                         * |    | viewport |    |
                         * |    |          |    |
                         * ----------------------
                         * */
                        mViewportWidth = sBaseValue * viewportRatio;
                        mViewportHeight = sBaseValue;

                        mTextureWidth = sBaseValue * textureRatio;
                        mTextureHeight = sBaseValue;
                    } else {
                        /**
                         * crop height of texture
                         *
                         * texture
                         * -----------------------
                         * |---------------------|
                         * |                     |
                         * |      viewport       |
                         * |                     |
                         * |---------------------|
                         * -----------------------
                         * */
                        mViewportWidth = sBaseValue;
                        mViewportHeight = sBaseValue / viewportRatio;

                        mTextureWidth = sBaseValue;
                        mTextureHeight = sBaseValue / textureRatio;
                    }
                    break;
                case MDVRLibrary.PROJECTION_MODE_PLANE_FIT:
                default:
                    if (viewportRatio > textureRatio){
                        /**
                         * fit height of viewport
                         *
                         * viewport
                         * ---------------------
                         * |    |         |    |
                         * |    | texture |    |
                         * |    |         |    |
                         * ---------------------
                         * */
                        mViewportWidth = sBaseValue * viewportRatio ;
                        mViewportHeight = sBaseValue;

                        mTextureWidth = sBaseValue * textureRatio;
                        mTextureHeight = sBaseValue;
                    } else {
                        /**
                         * fit width of viewport
                         *
                         * viewport
                         * -----------------------
                         * |---------------------|
                         * |                     |
                         * |       texture       |
                         * |                     |
                         * |---------------------|
                         * -----------------------
                         * */
                        mViewportWidth = sBaseValue;
                        mViewportHeight = sBaseValue / viewportRatio;

                        mTextureWidth = sBaseValue;
                        mTextureHeight = sBaseValue / textureRatio;
                    }
                    break;
            }
        }

        public float getViewportWidth(){
            return mViewportWidth;
        }

        public float getViewportHeight(){
            return mViewportHeight;
        }

        public float getTextureWidth(){
            return mTextureWidth;
        }

        public float getTextureHeight(){
            return mTextureHeight;
        }
    }

    private class OrthogonalDirectorFactory extends MD360DirectorFactory{
        @Override
        public MD360Director createDirector(int index) {
            return new TinyPlanetProjection.OrthogonalDirector(new MD360Director.Builder());
        }
    }

    private class OrthogonalDirector extends MD360Director{

        private final float sNearBase;

        private OrthogonalDirector(Builder builder) {
            super(builder);
            sNearBase = getNear();
        }

        @Override
        public void setDeltaX(float mDeltaX) {
            // nop
        }

        @Override
        public void setDeltaY(float mDeltaY) {
            // nop
        }

        @Override
        public void updateSensorMatrix(float[] sensorMatrix) {
            // nop
        }

        @Override
        protected void updateProjection(){
            planeScaleCalculator.setViewportRatio(getRatio());
            planeScaleCalculator.calculate();
            float scale = sNearBase / getNear();
            final float left = - planeScaleCalculator.getViewportWidth() / 2 * scale;
            final float right = planeScaleCalculator.getViewportWidth() / 2 * scale;
            final float bottom = - planeScaleCalculator.getViewportHeight() / 2 * scale;
            final float top = planeScaleCalculator.getViewportHeight() / 2 * scale;
            final float far = 500;
            Matrix.orthoM(getProjectionMatrix(), 0, left, right, bottom, top, 1, far);
        }
    }
}

