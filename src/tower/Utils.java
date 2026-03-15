package tower;

import battlecode.common.*;

public class Utils {

    // Array arah untuk iterasi deterministik
    public static final Direction[] directions = {
        Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST,
        Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST,
    };


    static MapLocation[] pastLocations = new MapLocation[25];
    static int pastLocIndex = 0;

    public static void moveToTarget(RobotController rc, MapLocation target) throws GameActionException {
        if (!rc.isMovementReady() || target == null) return;

        MapLocation myLoc = rc.getLocation();
        Direction forward = myLoc.directionTo(target);
        if (forward == Direction.CENTER) return;

        rc.setIndicatorLine(myLoc, target, 255, 255, 0); 

        // Prioritas gerakkan
        Direction[] group1 = {forward, forward.rotateLeft(), forward.rotateRight()};
        Direction[] group2 = {forward.rotateLeft().rotateLeft(), forward.rotateRight().rotateRight()};
        Direction[] group3 = {forward.opposite().rotateRight(), forward.opposite().rotateLeft(), forward.opposite()};
        
        Direction[][] allGroups = {group1, group2, group3};

        Direction bestDir = Direction.CENTER;
        int bestScore = Integer.MAX_VALUE; 

        // Evaluasi semua arah sesuai prioritas
        for (int g = 0; g < allGroups.length; g++) {
            Direction[] currentGroup = allGroups[g];
            
            for (Direction dir : currentGroup) {
                if (rc.canMove(dir)) {
                    MapLocation nextLoc = myLoc.add(dir);
                    
                    int baseScore = g * 10000;

                    int penalty = 0;
                    for (int i = 0; i < pastLocations.length; i++) {
                        int memIdx = (pastLocIndex - 1 - i + pastLocations.length) % pastLocations.length;
                        MapLocation pastLoc = pastLocations[memIdx];
                        
                        if (pastLoc != null && pastLoc.equals(nextLoc)) {
                            penalty = 100000 - (i * 1000); 
                            break;
                        }
                    }
                    
                    int dist = nextLoc.distanceSquaredTo(target);
                    int totalScore = baseScore + penalty + dist;

                    if (totalScore < bestScore) {
                        bestScore = totalScore;
                        bestDir = dir;
                    }
                }
            }
        }

        if (bestDir != Direction.CENTER) {
            rc.move(bestDir);
            pastLocations[pastLocIndex % pastLocations.length] = myLoc.add(bestDir);
            pastLocIndex++;
        }
    }

    public static void retreatToPaintTower(RobotController rc, MapLocation target) throws GameActionException {
        moveToTarget(rc, target);
    }

    public static boolean isNearMiddle(MapLocation loc, int width, int height) {
        int midY = height / 2;
        return Math.abs(loc.y - midY) <= 5;
    }

    public static void tryUpgradeTowers(RobotController rc) throws GameActionException {
        int round = rc.getRoundNum();

        // Tentukan prioritas upgrade berdasarkan round
        boolean allowMoney = false;
        boolean allowDefense = false;
        boolean allowPaint = false;

        if (round <= 600) {
            allowMoney = true;
        } else if (round <= 1000) {
            allowDefense = true;
            allowPaint = true;
        } else if (round <= 1400) {
            allowMoney = true;
        } else {
            allowDefense = true;
            allowPaint = true;
        }

        // Cari tower untuk di upgrade
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        
        RobotInfo bestTarget = null;
        int highestPriority = -1;

        for (RobotInfo ally : allies) {
            UnitType type = ally.getType();
            
            if (type.isTowerType() && rc.canUpgradeTower(ally.getLocation())) {
                
                boolean isMoney = (type == UnitType.LEVEL_ONE_MONEY_TOWER || type == UnitType.LEVEL_TWO_MONEY_TOWER);
                boolean isDefense = (type == UnitType.LEVEL_ONE_DEFENSE_TOWER || type == UnitType.LEVEL_TWO_DEFENSE_TOWER);
                boolean isPaint = (type == UnitType.LEVEL_ONE_PAINT_TOWER || type == UnitType.LEVEL_TWO_PAINT_TOWER);

                if ((allowMoney && isMoney) || (allowDefense && isDefense) || (allowPaint && isPaint)) {
                    
                    int priority = 0;
                    if (isMoney) priority = 3;       // Money nomor 1
                    else if (isDefense) priority = 2; // Defense nomor 2
                    else if (isPaint) priority = 1;   // Paint nomor 3

                    if (priority > highestPriority) {
                        highestPriority = priority;
                        bestTarget = ally;
                    }
                }
            }
        }

        if (bestTarget != null) {
            rc.upgradeTower(bestTarget.getLocation());
        }
    }
}