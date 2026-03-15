package aggressive;

import java.util.Random;

import battlecode.common.*;;

public class MopperBot extends Controller {
    private final int ATTACK_RADIUS_SQUARED = 2;
    private Direction selectedMopDirection = null;
    private MapLocation selectedEnemyTile = null;
    private MapLocation selectedTransferLocation = null;
    private int selectedTransferAmount = 0;
    private int selectedTransferAllyPaint = 0;
    private int transferPriority = 0;

    public MopperBot(RobotController rc) {
        this.rc = rc;
        this.rng = new Random(rc.getID());
        lastLocation = rc.getLocation();
        setupDefaultTarget();
    }

    public void run() throws GameActionException {
        if (rc.getLocation().isAdjacentTo(moveTarget)) {
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

        candidates.add(new Action(4, this::canMopEnemy, this::mopEnemy));

        candidates.add(new Action(2, this::canMopTile, this::mopTile));

        transferPriority = evaluateTransferPriority();
        candidates.add(new Action(transferPriority, this::canTransferPaint, this::transferPaint));

        candidates.add(new Action(2, this::canMoveToTarget, this::moveToTarget));
    }

    private boolean canMopEnemy() {
        try {
            if (!rc.isActionReady()) {
                return false;
            }

            selectedMopDirection = null;
            Direction[] cardinals = { Direction.EAST, Direction.NORTH, Direction.WEST, Direction.SOUTH };
            int[] robotsInCardinal = { 0, 0, 0, 0 };
            MapLocation cur = rc.getLocation();
            int bestSwing = 0;

            for (int i = 0; i < cardinals.length; i++) {
                Direction direction = cardinals[i];
                RobotInfo[] targets = { rc.senseRobotAtLocation(cur.add(direction)),
                        rc.senseRobotAtLocation(cur.add(direction.rotateLeft())),
                        rc.senseRobotAtLocation(cur.add(direction.rotateRight())) };

                for (RobotInfo robot : targets) {
                    if (robot != null) {
                        if (robot.getTeam() != rc.getTeam() && !isTower(robot)) {
                            robotsInCardinal[i]++;
                        }
                    }
                }
                if (robotsInCardinal[i] > robotsInCardinal[bestSwing]) {
                    bestSwing = i;
                }
            }

            if (robotsInCardinal[bestSwing] > 0 && rc.canMopSwing(cardinals[bestSwing])) {
                selectedMopDirection = cardinals[bestSwing];
                return true;
            }

            return false;
        } catch (GameActionException e) {
            throw new RuntimeException(e);
        }
    }

    private void mopEnemy() {
        if (selectedMopDirection == null) {
            return;
        }
        try {
            rc.mopSwing(selectedMopDirection);
        } catch (GameActionException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean canMopTile() {
        try {
            if (!rc.isActionReady()) {
                return false;
            }

            selectedEnemyTile = null;
            MapInfo[] nearbyTiles = rc.senseNearbyMapInfos(2);
            for (MapInfo mapInfo : nearbyTiles) {
                if (mapInfo.getPaint().isEnemy()) {
                    if (rc.canAttack(mapInfo.getMapLocation())) {
                        selectedEnemyTile = mapInfo.getMapLocation();
                        return true;
                    }
                }
            }
            return false;
        } catch (GameActionException e) {
            throw new RuntimeException(e);
        }
    }

    private void mopTile() {
        if (selectedEnemyTile == null) {
            return;
        }
        try {
            rc.attack(selectedEnemyTile);
        } catch (GameActionException e) {
            throw new RuntimeException(e);
        }
    }

    private int evaluateTransferPriority() throws GameActionException {
        selectedTransferLocation = null;
        selectedTransferAmount = 0;
        selectedTransferAllyPaint = 0;

        if (!rc.isActionReady()) {
            return 0;
        }

        RobotInfo[] allies = rc.senseNearbyRobots(2, rc.getTeam());
        int myPaint = Math.min(rc.getPaint(), 100);
        int bestScore = Integer.MIN_VALUE;

        for (RobotInfo ally : allies) {
            if (ally.getType().isTowerType()) {
                continue;
            }

            int allyCapacity = ally.getType().paintCapacity;
            int allyPaint = ally.getPaintAmount();
            int allyNeed = allyCapacity - ally.getPaintAmount();

            if (allyNeed <= 0) {
                continue;
            }

            int maxGive = Math.max(0, myPaint - 30);
            int amount = Math.min(allyNeed, maxGive);
            if (amount <= 0) {
                continue;
            }

            if (!rc.canTransferPaint(ally.getLocation(), amount)) {
                continue;
            }

            int score = allyNeed + amount;
            if (score > bestScore) {
                bestScore = score;
                selectedTransferLocation = ally.getLocation();
                selectedTransferAmount = amount;
                selectedTransferAllyPaint = allyPaint;
            }
        }

        if (selectedTransferLocation == null) {
            return 0;
        }

        int diff = myPaint - selectedTransferAllyPaint;
        if (diff >= 50) {
            return 3;
        }
        if (diff >= 20) {
            return 2;
        }
        return 1;
    }

    private boolean canTransferPaint() {
        return selectedTransferLocation != null
                && selectedTransferAmount > 0
                && rc.isActionReady()
                && rc.canTransferPaint(selectedTransferLocation, selectedTransferAmount);
    }

    private void transferPaint() {
        if (!canTransferPaint()) {
            return;
        }
        try {
            rc.transferPaint(selectedTransferLocation, selectedTransferAmount);
        } catch (GameActionException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean canMoveToTarget() {
        return rc.isMovementReady() && moveTarget != null;
    }

    private void moveToTarget() {
        try {
            moveToTargetWithWallFollowing(ATTACK_RADIUS_SQUARED);
        } catch (GameActionException e) {
            throw new RuntimeException(e);
        }
    }

}
