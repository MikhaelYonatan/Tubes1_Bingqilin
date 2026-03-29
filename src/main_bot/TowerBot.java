package main_bot;

import java.util.Random;

import battlecode.common.*;

public class TowerBot extends Controller {
    private static final int LOW_HEALTH_THRESHOLD = 120;
    private static final int MANY_ENEMY_COUNT = 3;
    private static final int MANY_ENEMY_PAINT_COUNT = 8;
    private static final int BIT_ENEMY_PAINT_COUNT = 3;

    int robotBuiltCount = 0;
    private RobotInfo[] nearbyEnemies = new RobotInfo[0];
    private RobotInfo singleEnemy = null;
    private int enemyPaintCount = 0;
    private MapLocation selectedAttackLocation = null;
    private MapLocation selectedSplasherSpawnLocation = null;
    private MapLocation selectedMopperSpawnLocation = null;
    private MapLocation selectedDefaultSpawnLocation = null;

    public TowerBot(RobotController rc) {
        this.rc = rc;
        rng = new Random(rc.getID());
    }

    public void run() throws GameActionException {
        setupDefaultTarget();
        setupActionCandidates();
        Action selectedAction = selectAction();
        while (selectedAction != null) {
            selectedAction.procedure.run();
            selectedAction = selectAction();
        }
    }

    private void analyzeBattlefield() throws GameActionException {
        nearbyEnemies = rc.senseNearbyRobots(9, rc.getTeam().opponent());
        singleEnemy = nearbyEnemies.length == 1 ? nearbyEnemies[0] : null;

        enemyPaintCount = 0;
        for (MapInfo mapInfo : rc.senseNearbyMapInfos()) {
            if (mapInfo.getPaint().isEnemy()) {
                enemyPaintCount++;
            }
        }

        selectedAttackLocation = null;
        if (singleEnemy != null
                && !isTower(singleEnemy)
                && singleEnemy.getHealth() <= LOW_HEALTH_THRESHOLD
                && rc.isActionReady()
                && rc.canAttack(singleEnemy.getLocation())) {
            selectedAttackLocation = singleEnemy.getLocation();
        }

        selectedSplasherSpawnLocation = findSpawnLocation(UnitType.SPLASHER);
        selectedMopperSpawnLocation = findSpawnLocation(UnitType.MOPPER);
        selectedDefaultSpawnLocation = findSpawnLocation(UnitType.SOLDIER);
    }

    private MapLocation findSpawnLocation(UnitType unitType) {
        Direction baseDirection = rc.getLocation().directionTo(moveTarget);
        if (baseDirection == Direction.CENTER) {
            baseDirection = Direction.NORTH;
        }

        Direction direction = baseDirection;
        for (int i = 0; i < 8; i++) {
            MapLocation location = rc.getLocation().add(direction);
            if (rc.canBuildRobot(unitType, location)) {
                return location;
            }
            direction = direction.rotateLeft();
        }
        return null;
    }

    private boolean shouldSpawnSplasher() {
        return nearbyEnemies.length >= MANY_ENEMY_COUNT || enemyPaintCount >= MANY_ENEMY_PAINT_COUNT;
    }

    private boolean shouldSpawnMopper() {
        boolean singleHighHealthEnemy = singleEnemy != null && singleEnemy.getHealth() > LOW_HEALTH_THRESHOLD;
        boolean bitEnemyPaint = enemyPaintCount >= BIT_ENEMY_PAINT_COUNT && enemyPaintCount < MANY_ENEMY_PAINT_COUNT;
        return singleHighHealthEnemy || bitEnemyPaint;
    }

    private boolean canAttackEnemy() {
        return selectedAttackLocation != null;
    }

    private void attackEnemy() {
        if (selectedAttackLocation == null) {
            return;
        }
        try {
            rc.attack(selectedAttackLocation);
        } catch (GameActionException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean canSpawnSplasher() {
        return rc.isActionReady() && selectedSplasherSpawnLocation != null && shouldSpawnSplasher();
    }

    private void spawnSplasher() {
        if (selectedSplasherSpawnLocation == null) {
            return;
        }
        try {
            rc.buildRobot(UnitType.SPLASHER, selectedSplasherSpawnLocation);
            robotBuiltCount++;
        } catch (GameActionException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean canSpawnMopper() {
        return rc.isActionReady() && selectedMopperSpawnLocation != null && shouldSpawnMopper();
    }

    private void spawnMopper() {
        if (selectedMopperSpawnLocation == null) {
            return;
        }
        try {
            rc.buildRobot(UnitType.MOPPER, selectedMopperSpawnLocation);
            robotBuiltCount++;
        } catch (GameActionException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean canSpawnDefault() {
        if (!rc.isActionReady() || selectedDefaultSpawnLocation == null) {
            return false;
        }
        if (nearbyEnemies.length == 0) {
            return true;
        }
        return !shouldSpawnSplasher() && !shouldSpawnMopper() && !canAttackEnemy();
    }

    private void spawnDefault() {
        if (selectedDefaultSpawnLocation == null) {
            return;
        }
        try {
            rc.buildRobot(UnitType.SOLDIER, selectedDefaultSpawnLocation);
            robotBuiltCount++;
        } catch (GameActionException e) {
            throw new RuntimeException(e);
        }
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
        analyzeBattlefield();
        candidates.clear();

        candidates.add(new Action(4, this::canAttackEnemy, this::attackEnemy));
        candidates.add(new Action(3, this::canSpawnSplasher, this::spawnSplasher));
        candidates.add(new Action(2, this::canSpawnMopper, this::spawnMopper));
        candidates.add(new Action(1, this::canSpawnDefault, this::spawnDefault));
    }
}
