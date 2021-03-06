/*
 * Copyright (c) 2016 theKidOfArcrania
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import java.util.ArrayList;

import javafx.animation.AnimationTimer;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.ConditionalFeature;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Point3D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.PerspectiveCamera;
import javafx.scene.PointLight;
import javafx.scene.Scene;
import javafx.scene.SceneAntialiasing;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.Mesh;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.Shape3D;
import javafx.scene.shape.Sphere;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Translate;
import javafx.stage.Stage;
import javafx.util.Duration;

public class CubeWorld extends Application
{
	private static final double WALK_SIN_STRETCH = .3;
	private static final double WALK_SIN_SHIFT = 1.2;
	private static final double TURN_VELOCITY = 45;
	private static final double MOVE_VELOCITY = 200;
	private static final double WALK_CYCLE = 5;
	private static final double WALK_LOWER = 20;
	private static final double WALK_UPPER = 0;

	public static void main(String[] args)
	{
		Application.launch(args);
	}

	private static int collidingState(double beforeMin, double beforeMax, double objMin, double objMax, double move)
	{
		double min = beforeMin + move;
		double max = beforeMax + move;

		if (max > objMin && objMax > min) //Intersecting
		{
			double maxOutside = max - objMax;
			double minOutside = objMin - min;

			if (move == 0 || maxOutside == minOutside || maxOutside <= 0 && minOutside <= 0)
				return 1; //Receding
			else
				return maxOutside < minOutside ^ move < 0 ? 2 /*Advancing*/ : 1 /*Receding*/;
		}
		else
			return 0; //No intersection
	}

	private static Box createBox(double x, double y, double z, double width, double height, double depth, Color c)
	{
		Box cube = new Box(width, height, depth);
		cube.setTranslateX(x + width / 2);
		cube.setTranslateY(y + height / 2);
		cube.setTranslateZ(z + depth / 2);
		cube.setMaterial(new PhongMaterial(c));
		return cube;
	}

	private static Mesh createCrystal(int latFaces, float side, float bodyHeight, float pyramidHeight)
	{
		TriangleMesh mesh = new TriangleMesh();
		mesh.getTexCoords().addAll(0, 0);

		double totalExterior = 2 * Math.PI;
		double[] regularPoints = new double[latFaces * 2];
		double x = 0;
		double y = 0;
		double angle = 0;
		for (int i = 0; i < latFaces; i++, angle += totalExterior / latFaces)
		{
			regularPoints[i * 2] = x;
			regularPoints[i * 2 + 1] = y;
			x += Math.cos(angle) * side;
			y += Math.sin(angle) * side;
		}

		double centerX, centerZ;
		if ((latFaces & 1) == 0)
		{
			centerX = regularPoints[latFaces];
			centerZ = regularPoints[latFaces + 1];
		}
		else
		{
			centerX = (regularPoints[latFaces - 1] + regularPoints[latFaces + 1]) / 2;
			centerZ = (regularPoints[latFaces] + regularPoints[latFaces + 2]) / 2;
		}
		centerX = (centerX + regularPoints[0]) / 2;
		centerZ = (centerZ + regularPoints[1]) / 2;

		// Add top pyramid.
		mesh.getPoints().addAll(0, -pyramidHeight - bodyHeight / 2, 0);
		for (int i = 0; i < latFaces; i++)
		{
			mesh.getPoints().addAll((float) (regularPoints[i * 2] - centerX), -bodyHeight / 2, (float) (regularPoints[i * 2 + 1] - centerZ));
			if (i == 0)
				mesh.getFaces().addAll(0, 0, latFaces, 0, 1, 0);
			else
				mesh.getFaces().addAll(0, 0, i, 0, i + 1, 0);
		}

		// Add body prism
		for (int i = 0; i < latFaces; i++)
		{
			mesh.getPoints().addAll((float) (regularPoints[i * 2] - centerX), bodyHeight / 2, (float) (regularPoints[i * 2 + 1] - centerZ));
			if (i == 0)
			{
				mesh.getFaces().addAll(latFaces, 0, latFaces * 2, 0, latFaces + 1, 0);
				mesh.getFaces().addAll(latFaces, 0, latFaces + 1, 0, 1, 0);
			}
			else
			{
				int across = i + latFaces;
				mesh.getFaces().addAll(i, 0, across, 0, across + 1, 0);
				mesh.getFaces().addAll(i, 0, across + 1, 0, i + 1, 0);
			}
		}

		// Add bottom pyramid
		int last = latFaces * 2 + 1;
		mesh.getPoints().addAll(0, pyramidHeight + bodyHeight / 2, 0);
		for (int i = 0; i < latFaces; i++)
			if (i == 0)
				mesh.getFaces().addAll(last, 0, latFaces + 1, 0, latFaces * 2, 0);
			else
				mesh.getFaces().addAll(last, 0, i + latFaces + 1, 0, i + latFaces, 0);

		for (int i = 0; i < mesh.getFaces().size(); i += 6)
			mesh.getFaceSmoothingGroups().addAll(0);
		return mesh;
	}

	private static Group createOpenCube(int width, int height, int depth, int thickness)
	{
		Group cube = new Group();
		cube.getChildren().addAll(
				createBox(0, 0, 0, thickness, height, depth, Color.ALICEBLUE),
				createBox(0, 0, 0, width, thickness, depth, Color.GREEN),
				createBox(0, 0, 0, width, height, thickness, Color.ALICEBLUE),
				createBox(width, 0, 0, thickness, height, depth, Color.ALICEBLUE),
				createBox(0, height, 0, width, thickness, depth, Color.DARKBLUE),
				createBox(0, 0, depth, width, height, thickness, Color.ALICEBLUE));
		return cube;
	}

	private static void rotate360(Rotate r, Duration cycleDuration)
	{
		Timeline rotating = new Timeline();
		rotating.getKeyFrames().addAll(new KeyFrame(Duration.seconds(0), new KeyValue(r.angleProperty(), 0)), new KeyFrame(cycleDuration,
				new KeyValue(r.angleProperty(), 360)));
		rotating.setCycleCount(-1);
		rotating.playFromStart();
	}
	private boolean forward = false;
	private boolean backward = false;
	private boolean up = false;
	private boolean down = false;
	private boolean left = false;
	private boolean right = false;
	private double before = -1;
	private double walkFrame = 0;
	private final Sphere cameraBody = new Sphere(50);
	private final Rotate elevate = new Rotate(10, Rotate.X_AXIS);
	private final Rotate heading = new Rotate(0, Rotate.Y_AXIS);
	private final Translate pos = new Translate(100, 900, 100);
	private final PerspectiveCamera camera = new PerspectiveCamera(true);
	private final ArrayList<Shape3D> solidBodies = new ArrayList<>();

	@Override
	public void start(Stage primaryStage) throws Exception
	{
		if (!Platform.isSupported(ConditionalFeature.SCENE3D))
		{
			Alert support = new Alert(AlertType.ERROR);
			support.setTitle("3D Test");
			support.setContentText("This computer does not support Scene3D.");
			support.showAndWait();
			System.exit(1);
		}

		primaryStage.setResizable(false);

		Box testBox = new Box(5, 5, 5);
		testBox.setMaterial(new PhongMaterial(Color.RED));
		// testBox.setDrawMode(DrawMode.LINE);

		Group cube = createOpenCube(2000,1000,2000,10);
		addSolidBodies(cube);

		PhongMaterial shinyBlue = new PhongMaterial(Color.AQUA);
		shinyBlue.setSpecularColor(Color.WHITE);

		MeshView crystal = new MeshView(createCrystal(6, 100f, 200f, 100f));
		crystal.setMaterial(shinyBlue);
		crystal.setTranslateX(500);
		crystal.setTranslateY(800);
		crystal.setTranslateZ(500);
		Rotate r = new Rotate();
		r.setAxis(Rotate.Y_AXIS);
		rotate360(r, Duration.seconds(2));
		crystal.getTransforms().add(r);
		addSolidBodies(crystal);

		PointLight light = new PointLight(Color.WHITE);
		Group cameraGroup = new Group();

		// Create and position camera
		// rotate360(xRotate);
		camera.setFieldOfView(78);
		camera.setFarClip(10000);
		camera.setVerticalFieldOfView(false);
		cameraGroup.getChildren().addAll(camera, light, cameraBody);
		cameraGroup.getTransforms().addAll(pos, elevate, heading);
		addSolidBodies(cameraBody);

		// Build the Scene Graph
		Group root = new Group();
		root.getChildren().addAll(cameraGroup, testBox, cube, crystal);
		Scene scene = new Scene(root, 1000, 1000, true, SceneAntialiasing.BALANCED);
		scene.setCamera(camera);
		scene.setFill(Color.BLACK);
		primaryStage.setScene(scene);
		primaryStage.show();
		primaryStage.setFullScreen(true);
		primaryStage.setFullScreenExitKeyCombination(KeyCombination.NO_MATCH);
		primaryStage.addEventFilter(KeyEvent.KEY_PRESSED, evt ->
		{
			switch (evt.getCode())
			{
			case W:
				forward = true;
				break;
			case A:
				left = true;
				break;
			case S:
				backward = true;
				break;
			case D:
				right = true;
				break;
			case Q:
				down = true;
				break;
			case E:
				up = true;
				break;
			}
		});
		primaryStage.addEventFilter(KeyEvent.KEY_RELEASED, evt ->
		{
			switch (evt.getCode())
			{
			case W:
				forward = false;
				break;
			case A:
				left = false;
				break;
			case S:
				backward = false;
				break;
			case D:
				right = false;
				break;
			case Q:
				down = false;
				break;
			case E:
				up = false;
				break;
			}
		});
		AnimationTimer tick = new AnimationTimer()
		{

			@Override
			public void handle(long xx)
			{
				double now = System.currentTimeMillis() * .001;
				if (before != -1)
					act(now - before);
				before = now;
			}

		};
		tick.start();
	}

	private void act(double time)
	{
		if (left ^ right)
		{
			double angle = heading.getAngle();
			double newAngle = angle + time * TURN_VELOCITY * (right ? 1 : -1);
			heading.setAngle(newAngle - Math.floor(newAngle / 360) * 360);
			elevate.setAxis(heading.transform(Rotate.X_AXIS));
		}

		if (forward ^ backward)
		{
			double sin = Math.sin(Math.toRadians(heading.getAngle()));
			double cos = Math.cos(Math.toRadians(heading.getAngle()));
			double walkAngle = walkFrame * Math.PI * 2;
			double dist = (Math.sin(walkAngle) * WALK_SIN_STRETCH + WALK_SIN_SHIFT) * MOVE_VELOCITY * time * (forward ? 1 : -1);

			if (move(cameraBody, pos, new Point3D(sin * dist, 0, cos * dist)))
			{
				camera.setTranslateY((Math.sin(walkAngle) + 1.0) * (WALK_UPPER - WALK_LOWER) + WALK_LOWER);
				walkFrame += time * (forward ? 1 : -1);
				walkFrame -= Math.floor(walkFrame / WALK_CYCLE) * WALK_CYCLE;
			}
		}

		if (up ^ down)
		{
			double angle = elevate.getAngle();
			double newAngle = angle + time * TURN_VELOCITY * (up ? 1 : -1);
			elevate.setAngle(Math.min(90, Math.max(-90, newAngle)));
		}
	}

	private void addSolidBodies(Node body)
	{
		ArrayList<Node> tests = new ArrayList<>();
		tests.add(body);
		while (!tests.isEmpty())
		{
			Node object = tests.remove(tests.size() - 1);
			if (object instanceof Shape3D)
				solidBodies.add((Shape3D)object);
			else if (object instanceof Parent)
				for (Node child : ((Parent) object).getChildrenUnmodifiable())
					tests.add(child);
		}
	}

	private boolean move(Shape3D mover, Translate pos, Point3D vector)
	{
		double oldX = pos.getX(), oldY = pos.getY(), oldZ = pos.getZ();

		if (solidBodies.contains(mover))
		{
			Bounds moverBounds = mover.localToScene(mover.getBoundsInLocal());
			for (Shape3D other : solidBodies)
			{
				if (other == mover)
					continue;
				Bounds otherBounds = other.localToScene(other.getBoundsInLocal());
				int xColliding = collidingState(moverBounds.getMinX(), moverBounds.getMaxX(), otherBounds.getMinX(), otherBounds.getMaxX(), vector.getX());
				int yColliding = collidingState(moverBounds.getMinY(), moverBounds.getMaxY(), otherBounds.getMinY(), otherBounds.getMaxY(), vector.getY());
				int zColliding = collidingState(moverBounds.getMinZ(), moverBounds.getMaxZ(), otherBounds.getMinZ(), otherBounds.getMaxZ(), vector.getZ());

				boolean intersecting = xColliding > 0 && yColliding > 0 && zColliding > 0;
				if (xColliding + yColliding + zColliding > 3)
				{
					pos.setX(oldX);
					pos.setY(oldY);
					pos.setZ(oldZ);
					return false;
				}
			}
		}

		pos.setX(oldX + vector.getX());
		pos.setY(oldY + vector.getY());
		pos.setZ(oldZ + vector.getZ());
		return true;
	}
}
