package tower;

import battlecode.common.*;

public class Utils {

    public static final Direction[] directions = {
        Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST,
        Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST,
    };

    static MapLocation[] pastLocations = new MapLocation[25];
    static int pastLocIndex = 0;

    // 1. NAVIGASI & PERGERAKAN
    public static void moveToTarget(RobotController rc, MapLocation target) throws GameActionException {
        if (!rc.isMovementReady() || target == null) return;

        MapLocation myLoc = rc.getLocation();
        Direction forward = myLoc.directionTo(target);
        if (forward == Direction.CENTER) return;

        rc.setIndicatorLine(myLoc, target, 255, 255, 0); 

        Direction[] group1 = {forward, forward.rotateLeft(), forward.rotateRight()};
        Direction[] group2 = {forward.rotateLeft().rotateLeft(), forward.rotateRight().rotateRight()};
        Direction[] group3 = {forward.opposite().rotateRight(), forward.opposite().rotateLeft(), forward.opposite()};
        
        Direction[][] allGroups = {group1, group2, group3};

        Direction bestDir = Direction.CENTER;
        int bestScore = Integer.MAX_VALUE; 

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

    // 2. UPGRADE TOWER
    public static void tryUpgradeTowers(RobotController rc) throws GameActionException {
        int round = rc.getRoundNum();

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
                    if (isMoney) priority = 3;       
                    else if (isDefense) priority = 2; 
                    else if (isPaint) priority = 1;   

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

    // 3. SISTEM RADIO LAMA (DEPRECATED)
    public static void shareRuinLocToAllies(RobotController rc, MapLocation ruinLoc) throws GameActionException {
        int messageContent = (123 << 16) | (ruinLoc.x << 8) | ruinLoc.y;
        RobotInfo[] allies = rc.senseNearbyRobots(20, rc.getTeam());
        int messageCount = 0;

        for (RobotInfo ally : allies) {
            if (ally.getType() == UnitType.SOLDIER) {
                if (rc.canSendMessage(ally.getLocation(), messageContent)) {
                    rc.sendMessage(ally.getLocation(), messageContent);
                    messageCount++;
                    if (messageCount >= 3) break; 
                }
            }
        }
    }

    public static MapLocation readRuinMessages(RobotController rc) throws GameActionException {
        Message[] msgs = rc.readMessages(-1); 
        
        for (Message msg : msgs) {
            int content = msg.getBytes(); 
            int header = (content >> 16) & 0xFF;
            if (header == 123) { 
                int x = (content >> 8) & 0xFF;
                int y = content & 0xFF;
                return new MapLocation(x, y); 
            }
        }
        return null;
    }

    // 4. MESH NETWORK V2
    public static final int MSG_RUIN_FOUND = 1;   
    public static final int MSG_TOWER_DONE = 2;   
    public static final int MSG_ENEMY_TOWER = 3;  

    public static void sendRadio(RobotController rc, int header, MapLocation loc) throws GameActionException {
        if (loc == null) return;
        
        MapInfo currentTile = rc.senseMapInfo(rc.getLocation());
        if (!currentTile.getPaint().isAlly()) return; 

        int msg = (header << 16) | (loc.x << 8) | loc.y;
        RobotInfo[] allies = rc.senseNearbyRobots(20, rc.getTeam());
        RobotInfo bestRelay = null;

        for (RobotInfo ally : allies) {
            if (rc.canSendMessage(ally.getLocation(), msg)) {
                bestRelay = ally;
                if (ally.getType().isTowerType()) break; 
            }
        }

        if (bestRelay != null) {
            rc.sendMessage(bestRelay.getLocation(), msg);
        }
    }

    public static int decodeHeader(int msg) { return (msg >> 16) & 0xFF; }
    public static MapLocation decodeLoc(int msg) { return new MapLocation((msg >> 8) & 0xFF, msg & 0xFF); }
}