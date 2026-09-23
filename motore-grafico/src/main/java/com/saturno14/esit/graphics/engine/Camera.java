package com.saturno14.esit.graphics.engine;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Camera a volo libero (free-fly), utile in fase di sviluppo per girare intorno al mondo.
 */
public class Camera {

    private final Vector3f position = new Vector3f(10f, 15f, 30f);
    private float yaw = -90f;
    private float pitch = -20f;

    private float fovDegrees = 60f;
    private float aspectRatio;
    private static final float NEAR_PLANE = 0.1f;
    private static final float FAR_PLANE = 500f;

    public Camera(float aspectRatio) {
        this.aspectRatio = aspectRatio;
    }

    public void setAspectRatio(float aspectRatio) {
        this.aspectRatio = aspectRatio;
    }

    public Vector3f getPosition() {
        return position;
    }

    public Vector3f getForward() {
        float yawRad = (float) Math.toRadians(yaw);
        float pitchRad = (float) Math.toRadians(pitch);
        return new Vector3f(
                (float) (Math.cos(yawRad) * Math.cos(pitchRad)),
                (float) Math.sin(pitchRad),
                (float) (Math.sin(yawRad) * Math.cos(pitchRad))
        ).normalize();
    }

    public Vector3f getRight() {
        return getForward().cross(new Vector3f(0, 1, 0), new Vector3f()).normalize();
    }

    public void move(Vector3f delta) {
        position.add(delta);
    }

    public void rotate(float deltaYaw, float deltaPitch) {
        yaw += deltaYaw;
        pitch += deltaPitch;
        if (pitch > 89f) pitch = 89f;
        if (pitch < -89f) pitch = -89f;
    }

    public Matrix4f getViewMatrix() {
        Vector3f target = new Vector3f(position).add(getForward());
        return new Matrix4f().lookAt(position, target, new Vector3f(0, 1, 0));
    }

    public Matrix4f getProjectionMatrix() {
        return new Matrix4f().perspective((float) Math.toRadians(fovDegrees), aspectRatio, NEAR_PLANE, FAR_PLANE);
    }
}
