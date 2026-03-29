package main_bot;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.BooleanSupplier;

import battlecode.common.*;

public abstract class Controller {
    protected class Action {
        int priority;
        BooleanSupplier feasibilityFunction;
        Runnable procedure;

        public Action(int priority, BooleanSupplier feasibilityFunction, Runnable procedure) {
            this.priority = priority;
            this.feasibilityFunction = feasibilityFunction;
            this.procedure = procedure;
        }
    }

    final Direction[] directions = {
            Direction.NORTH,
            Direction.NORTHEAST,
            Direction.EAST,
            Direction.SOUTHEAST,
            Direction.SOUTH,
            Direction.SOUTHWEST,
            Direction.WEST,
            Direction.NORTHWEST,
    };

    protected RobotController rc;
    protected List<Action> candidates = new ArrayList<>();
    protected Random rng;
    protected MapLocation moveTarget;
    protected MapLocation lastLocation;
    protected boolean isWallFollowing = false;
    protected boolean followLeftWall = true;
    protected Direction wallFollowHeading = Direction.NORTH;
    protected int wallHitDistance = Integer.MAX_VALUE;

    /**
     * Run actions for one turn
     * 
     * @throws GameActionException
     */
    public abstract void run() throws GameActionException;

    /**
     * Select action according to priority and feasibility
     * 
     * @return selectedAction
     * @throws GameActionException
     */
    protected abstract Action selectAction() throws GameActionException;

    protected abstract void setupActionCandidates() throws GameActionException;

    protected void setupDefaultTarget() {
        int spawnX = rc.getLocation().x;
        int spawnY = rc.getLocation().y;
        int width = rc.getMapWidth();
        int height = rc.getMapHeight();
        MapLocation[] potentialTarget = {
                new MapLocation(spawnX, height - 1 - spawnY),
                new MapLocation(width - 1 - spawnX, spawnY),
                new MapLocation(width - 1 - spawnX, height - 1 - spawnY)
        };
        moveTarget = potentialTarget[(rc.getRoundNum()) % 3];
    }

    protected void setupRandomTarget() {
        int width = rc.getMapWidth();
        int height = rc.getMapHeight();
        int west = 0;
        int east = width - 1;
        int north = 0;
        int south = height - 1;
        int centerX = width / 2;
        int centerY = height / 2;

        MapLocation[] possibleTargets = {
                new MapLocation(west, north),
                new MapLocation(centerX, north),
                new MapLocation(east, north),
                new MapLocation(west, centerY),
                new MapLocation(centerX, centerY),
                new MapLocation(east, centerY),
                new MapLocation(west, south),
                new MapLocation(centerX, south),
                new MapLocation(east, south)
        };

        moveTarget = possibleTargets[rng.nextInt(possibleTargets.length)];
        resetNavigationState();
    }

    protected boolean isTower(RobotInfo robotInfo) {
        if (robotInfo == null) {
            return false;
        }
        switch (robotInfo.getType()) {
            case SPLASHER:
                return false;
            case SOLDIER:
                return false;
            case MOPPER:
                return false;
            default:
                return true;
        }
    }

    protected void resetNavigationState() {
        isWallFollowing = false;
        wallHitDistance = Integer.MAX_VALUE;
    }

    protected Direction getGreedyDirectionToTarget() {
        MapLocation current = rc.getLocation();
        Direction bestDirection = null;
        int bestDistance = Integer.MAX_VALUE;

        for (Direction direction : directions) {
            if (!rc.canMove(direction)) {
                continue;
            }

            MapLocation next = current.add(direction);
            int distance = next.distanceSquaredTo(moveTarget);
            if (moveTarget != null && direction == current.directionTo(moveTarget)) {
                distance -= 1;
            }

            if (lastLocation != null && next.equals(lastLocation)) {
                distance += 9;
            }

            if (distance < bestDistance) {
                bestDistance = distance;
                bestDirection = direction;
            }
        }

        return bestDirection;
    }

    protected Direction rotateTowardWall(Direction direction) {
        return followLeftWall ? direction.rotateLeft() : direction.rotateRight();
    }

    protected Direction rotateAwayFromWall(Direction direction) {
        return followLeftWall ? direction.rotateRight() : direction.rotateLeft();
    }

    protected Direction getWallFollowDirection() {
        Direction candidate = rotateTowardWall(wallFollowHeading);
        for (int i = 0; i < 8; i++) {
            if (rc.canMove(candidate)) {
                return candidate;
            }
            candidate = rotateAwayFromWall(candidate);
        }
        return null;
    }

    protected void moveToTargetWithWallFollowing(int resetDistanceSquared) throws GameActionException {
        if (!rc.isMovementReady() || moveTarget == null) {
            return;
        }

        if (rc.getLocation().isWithinDistanceSquared(moveTarget, resetDistanceSquared)) {
            resetNavigationState();
        }

        Direction targetDirection = rc.getLocation().directionTo(moveTarget);

        if (!isWallFollowing && rc.canMove(targetDirection)) {
            rc.move(targetDirection);
            return;
        }

        int currentDistance = rc.getLocation().distanceSquaredTo(moveTarget);
        if (!isWallFollowing) {
            isWallFollowing = true;
            wallHitDistance = currentDistance;
            followLeftWall = rng.nextBoolean();
            wallFollowHeading = targetDirection;
        }

        if (rc.canMove(targetDirection) && currentDistance < wallHitDistance) {
            isWallFollowing = false;
            wallHitDistance = Integer.MAX_VALUE;
            rc.move(targetDirection);
            return;
        }

        Direction wallFollowDirection = getWallFollowDirection();
        if (wallFollowDirection != null) {
            rc.move(wallFollowDirection);
            wallFollowHeading = wallFollowDirection;
            return;
        }

        Direction greedyDirection = getGreedyDirectionToTarget();
        if (greedyDirection != null) {
            rc.move(greedyDirection);
            wallFollowHeading = greedyDirection;
        }
    }
}
