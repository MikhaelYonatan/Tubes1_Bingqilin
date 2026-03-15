package aggressive;

import java.util.Random;

import battlecode.common.*;

public class SplasherBot extends Controller {
    private static final int SPLASH_SCORE_MIN = 6;
    private MapLocation selectedSplashLocation = null;
    private int selectedSplashScore = 0;

    public SplasherBot(RobotController rc) {
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

        evaluateSplashLocation();
        int splashPriority = selectedSplashScore >= 15 ? 3 : 2;
        candidates.add(new Action(splashPriority, this::canSplash, this::splash));

        candidates.add(new Action(1, this::canRoam, this::roam));
    }

    private void evaluateSplashLocation() throws GameActionException {
        selectedSplashLocation = null;
        selectedSplashScore = 0;

        if (!rc.isActionReady()) {
            return;
        }

        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos(4);
        int bestScore = 0;
        MapLocation bestLocation = null;

        for (MapInfo tile : nearbyTiles) {
            MapLocation loc = tile.getMapLocation();
            if (!rc.canAttack(loc)) {
                continue;
            }

            int score = 0;
            MapInfo[] splashed = rc.senseNearbyMapInfos(loc, 2);
            for (MapInfo mapInfo : splashed) {
                switch (mapInfo.getPaint()) {
                    case EMPTY:
                        score += 1;
                        break;
                    case ENEMY_PRIMARY:
                        score += 3;
                        break;
                    case ENEMY_SECONDARY:
                        score += 3;
                        break;
                    default:
                        break;
                }
            }
            RobotInfo[] splashedRobots = rc.senseNearbyRobots(loc, 4, rc.getTeam().opponent());
            score += 5 * splashedRobots.length;

            if (score > bestScore) {
                bestScore = score;
                bestLocation = loc;
            }
        }

        if (bestScore >= SPLASH_SCORE_MIN) {
            selectedSplashLocation = bestLocation;
            selectedSplashScore = bestScore;
        }
    }

    private boolean canSplash() {
        return rc.isActionReady() && selectedSplashLocation != null;
    }

    private void splash() {
        try {
            if (selectedSplashLocation != null && rc.canAttack(selectedSplashLocation)) {
                rc.attack(selectedSplashLocation);
                selectedSplashLocation = null;
                selectedSplashScore = 0;
            }
        } catch (GameActionException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean canRoam() {
        return rc.isMovementReady() && moveTarget != null;
    }

    private void roam() {
        try {
            moveToTargetWithWallFollowing(2);
        } catch (GameActionException e) {
            throw new RuntimeException(e);
        }
    }
}
