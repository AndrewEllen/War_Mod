package com.andye.warmod.phalanx.client;

import com.andye.warmod.phalanx.PhalanxBulletTrajectory;
import com.andye.warmod.warhead.client.render.WarheadRenderPipelines;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;

public final class PhalanxTracerRenderer {
    private static final double STREAK_TICKS = 1.25;
    private static final double MAX_VISUAL_AGE_TICKS = 180.0;
    private static final double TRACER_HALF_WIDTH = 0.085;

    private record Streak(
        Vec3 start,
        Vec3 end,
        float alpha
    ) {
    }

    private record Frame(
        Vec3 camera,
        List<Streak> streaks
    ) {
        private static final Frame EMPTY =
            new Frame(
                Vec3.ZERO,
                List.of()
            );
    }

    private static volatile Frame frame =
        Frame.EMPTY;

    private static boolean registered;

    private PhalanxTracerRenderer() {
    }

    public static void register() {
        if (registered) {
            return;
        }

        LevelExtractionEvents.END_EXTRACTION.register(
            PhalanxTracerRenderer::extract
        );

        LevelRenderEvents.COLLECT_SUBMITS.register(
            PhalanxTracerRenderer::render
        );

        registered = true;
    }

    private static void extract(
        final LevelExtractionContext context
    ) {
        CameraRenderState camera =
            context.levelState()
                .cameraRenderState;

        if (
            context.level() == null
            || camera == null
            || camera.pos == null
        ) {
            frame =
                Frame.EMPTY;

            return;
        }

        double now =
            context.level()
                .getGameTime()
                + context.deltaTracker()
                    .getGameTimeDeltaPartialTick(
                        true
                    );

        ArrayList<Streak> streaks =
            new ArrayList<>();

        for (
            PhalanxTracerManager.Tracer tracer
                : PhalanxTracerManager.snapshot(now)
        ) {
            double age =
                Math.max(
                    0.0,
                    now - tracer.startTime()
                );

            Vec3 end =
                PhalanxBulletTrajectory.position(
                    tracer.origin(),
                    tracer.velocity(),
                    age
                );

            Vec3 start =
                PhalanxBulletTrajectory.position(
                    tracer.origin(),
                    tracer.velocity(),
                    Math.max(
                        0.0,
                        age - STREAK_TICKS
                    )
                );

            if (
                !start.isFinite()
                || !end.isFinite()
            ) {
                continue;
            }

            double fadeStart =
                MAX_VISUAL_AGE_TICKS
                    - 20.0;

            float alpha =
                age <= fadeStart
                    ? 1.0F
                    : (float)Math.max(
                        0.12,
                        1.0
                            - (
                                age - fadeStart
                            ) / 20.0
                    );

            streaks.add(
                new Streak(
                    start,
                    end,
                    alpha
                )
            );
        }

        frame =
            new Frame(
                camera.pos,
                List.copyOf(streaks)
            );
    }

    private static void render(
        final LevelRenderContext context
    ) {
        Frame current =
            frame;

        if (
            current == Frame.EMPTY
            || context.poseStack() == null
        ) {
            return;
        }

        for (
            Streak streak
                : current.streaks()
        ) {
            PoseStack stack =
                context.poseStack();

            stack.pushPose();

            Vec3 offset =
                streak.start()
                    .subtract(
                        current.camera()
                    );

            stack.translate(
                offset.x,
                offset.y,
                offset.z
            );

            Vec3 direction =
                streak.end()
                    .subtract(
                        streak.start()
                    );

            context.submitNodeCollector()
                .submitCustomGeometry(
                    stack,
                    WarheadRenderPipelines
                        .NUCLEAR_FLASH,
                    (pose, buffer) ->
                        ribbons(
                            pose,
                            buffer,
                            direction,
                            streak.alpha()
                        )
                );

            stack.popPose();
        }
    }

    private static void ribbons(
        final PoseStack.Pose pose,
        final VertexConsumer buffer,
        final Vec3 direction,
        final float alpha
    ) {
        if (
            direction.lengthSqr()
                < 1.0E-8
        ) {
            return;
        }

        Vec3 forward =
            direction.normalize();

        Vec3 sideA =
            forward.cross(
                new Vec3(
                    0.0,
                    1.0,
                    0.0
                )
            );

        if (
            sideA.lengthSqr()
                < 1.0E-8
        ) {
            sideA =
                new Vec3(
                    1.0,
                    0.0,
                    0.0
                );
        }

        sideA =
            sideA.normalize()
                .scale(
                    TRACER_HALF_WIDTH
                );

        Vec3 sideB =
            forward.cross(sideA)
                .normalize()
                .scale(
                    TRACER_HALF_WIDTH
                );

        ribbon(
            pose,
            buffer,
            direction,
            sideA,
            alpha
        );

        ribbon(
            pose,
            buffer,
            direction,
            sideB,
            alpha
        );

        /*
         * A short, wider head makes the projectile visible even when the long
         * streak points almost directly toward the camera.
         */
        Vec3 headStart =
            direction.subtract(
                forward.scale(0.70)
            );

        ribbon(
            pose,
            buffer,
            direction.subtract(
                headStart
            ),
            sideA.scale(1.55),
            Math.min(
                1.0F,
                alpha + 0.15F
            ),
            headStart
        );

        ribbon(
            pose,
            buffer,
            direction.subtract(
                headStart
            ),
            sideB.scale(1.55),
            Math.min(
                1.0F,
                alpha + 0.15F
            ),
            headStart
        );
    }

    private static void ribbon(
        final PoseStack.Pose pose,
        final VertexConsumer buffer,
        final Vec3 end,
        final Vec3 side,
        final float alpha
    ) {
        ribbon(
            pose,
            buffer,
            end,
            side,
            alpha,
            Vec3.ZERO
        );
    }

    private static void ribbon(
        final PoseStack.Pose pose,
        final VertexConsumer buffer,
        final Vec3 end,
        final Vec3 side,
        final float alpha,
        final Vec3 offset
    ) {
        int leadingAlpha =
            (int)(255.0F * alpha);

        int trailingAlpha =
            (int)(120.0F * alpha);

        vertex(
            pose,
            buffer,
            offset.add(side),
            255,
            252,
            210,
            leadingAlpha
        );

        vertex(
            pose,
            buffer,
            offset.subtract(side),
            255,
            252,
            210,
            leadingAlpha
        );

        vertex(
            pose,
            buffer,
            offset.add(end)
                .subtract(side),
            255,
            134,
            22,
            trailingAlpha
        );

        vertex(
            pose,
            buffer,
            offset.add(end)
                .add(side),
            255,
            134,
            22,
            trailingAlpha
        );
    }

    private static void vertex(
        final PoseStack.Pose pose,
        final VertexConsumer buffer,
        final Vec3 position,
        final int red,
        final int green,
        final int blue,
        final int alpha
    ) {
        buffer
            .addVertex(
                pose,
                (float)position.x,
                (float)position.y,
                (float)position.z
            )
            .setColor(
                red,
                green,
                blue,
                alpha
            )
            .setUv(
                0.5F,
                0.5F
            )
            .setOverlay(0)
            .setLight(0xF000F0)
            .setNormal(
                pose,
                0.0F,
                1.0F,
                0.0F
            );
    }
}
