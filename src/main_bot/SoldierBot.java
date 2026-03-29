package main_bot;

import battlecode.common.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class SoldierBot extends Controller {
    private final int ATTACK_RADIUS = 3;
    private RobotInfo[] nearbyRobots = null;
    private RobotInfo selectedTowerTarget = null;
    private final Map<UnitType, Integer> towerDestructionPriority = new HashMap<>();

    public SoldierBot(RobotController rc) {
        this.rc = rc;
        rng = new Random(rc.getRoundNum());
        lastLocation = rc.getLocation();
        setupDefaultTarget();
        setupTowerPriority();
    }

    private void setupTowerPriority() {
        towerDestructionPriority.put(UnitType.LEVEL_ONE_PAINT_TOWER, 9);
        towerDestructionPriority.put(UnitType.LEVEL_ONE_DEFENSE_TOWER, 8);
        towerDestructionPriority.put(UnitType.LEVEL_ONE_MONEY_TOWER, 7);
        towerDestructionPriority.put(UnitType.LEVEL_TWO_MONEY_TOWER, 6);
        towerDestructionPriority.put(UnitType.LEVEL_TWO_PAINT_TOWER, 5);
        towerDestructionPriority.put(UnitType.LEVEL_TWO_DEFENSE_TOWER, 4);
        towerDestructionPriority.put(UnitType.LEVEL_THREE_DEFENSE_TOWER, 3);
        towerDestructionPriority.put(UnitType.LEVEL_THREE_MONEY_TOWER, 2);
        towerDestructionPriority.put(UnitType.LEVEL_THREE_PAINT_TOWER, 1);
    }

    private void updateSelectedTowerTarget() throws GameActionException {
        selectedTowerTarget = null;
        nearbyRobots = rc.senseNearbyRobots(ATTACK_RADIUS * ATTACK_RADIUS, rc.getTeam().opponent());
        if (nearbyRobots.length == 0) {
            return;
        }

        int bestScore = 0;
        for (RobotInfo robot : nearbyRobots) {
            int score = towerDestructionPriority.getOrDefault(robot.getType(), 0) * (3001 - robot.getHealth());
            if (score > bestScore && rc.canAttack(robot.getLocation())) {
                bestScore = score;
                selectedTowerTarget = robot;
            }
        }
    }

    private boolean canDestroyTower() throws GameActionException {
        if (!rc.isActionReady()) {
            return false;
        }
        updateSelectedTowerTarget();
        return selectedTowerTarget != null;
    }

    private void destroyTower() throws GameActionException {
        if (selectedTowerTarget == null) {
            return;
        }
        if (selectedTowerTarget.getHealth() <= 5) {
            setupRandomTarget();
        }
        rc.attack(selectedTowerTarget.getLocation());
    }

    private boolean canMoveToTower() {
        return rc.isMovementReady() && moveTarget != null;
    }

    private void moveToTower() throws GameActionException {
        moveToTargetWithWallFollowing(ATTACK_RADIUS * ATTACK_RADIUS);
    }

    private boolean canPaintCurrentTile() throws GameActionException {
        if (!rc.isActionReady() || !rc.canAttack(rc.getLocation())) {
            return false;
        }
        return rc.senseMapInfo(rc.getLocation()).getPaint() == PaintType.EMPTY;
    }

    private void paintCurrentTile() throws GameActionException {
        rc.attack(rc.getLocation());
    }

    private boolean canDestroyTowerSafe() {
        try {
            return canDestroyTower();
        } catch (GameActionException e) {
            throw new RuntimeException(e);
        }
    }

    private void destroyTowerSafe() {
        try {
            destroyTower();
        } catch (GameActionException e) {
            throw new RuntimeException(e);
        }
    }

    private void moveToTowerSafe() {
        try {
            moveToTower();
        } catch (GameActionException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean canPaintCurrentTileSafe() {
        try {
            return canPaintCurrentTile();
        } catch (GameActionException e) {
            throw new RuntimeException(e);
        }
    }

    private void paintCurrentTileSafe() {
        try {
            paintCurrentTile();
        } catch (GameActionException e) {
            throw new RuntimeException(e);
        }
    }

    public void run() throws GameActionException {
        if (rc.getLocation().isWithinDistanceSquared(moveTarget, ATTACK_RADIUS * ATTACK_RADIUS)
                && selectedTowerTarget == null) {
            setupRandomTarget();
        }

        setupActionCandidates();
        Action selectedAction = selectAction();
        while (selectedAction != null) {
            selectedAction.procedure.run();
            selectedAction = selectAction();
        }
        lastLocation = rc.getLocation();
    }

    protected Action selectAction() throws GameActionException {
        Action selectedAction = null;
        int bestPriority = Integer.MIN_VALUE;
        for (Action action : candidates) {
            if (action.priority > bestPriority && action.feasibilityFunction.getAsBoolean()) {
                bestPriority = action.priority;
                selectedAction = action;
            }
        }
        return selectedAction;
    }

    protected void setupActionCandidates() throws GameActionException {
        candidates.clear();

        candidates.add(new Action(3, this::canDestroyTowerSafe, this::destroyTowerSafe));
        candidates.add(new Action(2, this::canMoveToTower, this::moveToTowerSafe));
        candidates.add(new Action(1, this::canPaintCurrentTileSafe, this::paintCurrentTileSafe));
    }
}
