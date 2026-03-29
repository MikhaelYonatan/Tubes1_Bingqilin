package alternative_bots_1;

import battlecode.common.*;

public class Tower {

    static int splasherBuilt = 0;
    static int soldierBuilt = 0;
    static int mopperBuilt = 0;

    static int[] recentMessages = new int[30];
    static int msgIndex = 0;

    public static void runTower(RobotController rc) throws GameActionException {
        // 1. fase komunikasi (estafet sinyal jarak jauh)
        runRadioRelay(rc);

        // 2. fase pertahanan (sikat musuh dulu!)
        attackEnemies(rc);

        // 3. fase produksi (pabrik robot)
        spawnUnits(rc);
    }

    private static void runRadioRelay(RobotController rc) throws GameActionException {
        Message[] msgs = rc.readMessages(-1);
        
        int broadcastsThisTurn = 0; 

        for (Message m : msgs) {
            int msgContent = m.getBytes();

            boolean alreadyBroadcasted = false;
            for (int i = 0; i < recentMessages.length; i++) {
                if (recentMessages[i] == msgContent) {
                    alreadyBroadcasted = true;
                    break;
                }
            }

            if (!alreadyBroadcasted) {
                recentMessages[msgIndex % recentMessages.length] = msgContent;
                msgIndex++;

                if (broadcastsThisTurn < 15) {
                    rc.broadcastMessage(msgContent);
                    broadcastsThisTurn++;
                }
            }
        }
    }

    private static void attackEnemies(RobotController rc) throws GameActionException {
        if (!rc.isActionReady()) return; 

        RobotInfo[] enemies = rc.senseNearbyRobots(rc.getType().actionRadiusSquared, rc.getTeam().opponent());
        
        if (enemies.length == 0) return;

        // aturan 2: serangan aoe (area of effect)
        if (enemies.length >= 2) {
            if (rc.canAttack(null)) {
                rc.attack(null);
                return; 
            }
        }

        // aturan 1: serangan single (sniper mode)
        MapLocation bestTarget = null;
        
        for (RobotInfo enemy : enemies) {
            if (enemy.getType() == UnitType.SOLDIER) {
                bestTarget = enemy.getLocation();
                break;
            }
        }
        
        if (bestTarget == null) {
            bestTarget = enemies[0].getLocation();
        }

        if (bestTarget != null && rc.canAttack(bestTarget)) {
            rc.attack(bestTarget);
        }
    }

    private static void spawnUnits(RobotController rc) throws GameActionException {
        int roundNum = rc.getRoundNum();
        int currentChips = rc.getChips();

        if (roundNum > 1400 && currentChips <= 5000) {
            return; 
        } else if (roundNum > 1000 && roundNum <= 1400 && currentChips <= 2600) {
            return; 
        } else if (roundNum > 600 && roundNum <= 1000 && currentChips <= 1100) {
            return; 
        }

        UnitType myType = rc.getType();
        UnitType toBuild = null;

        boolean isMoneyTower = (myType == UnitType.LEVEL_ONE_MONEY_TOWER || myType == UnitType.LEVEL_TWO_MONEY_TOWER || myType == UnitType.LEVEL_THREE_MONEY_TOWER);
        boolean isPaintTower = (myType == UnitType.LEVEL_ONE_PAINT_TOWER || myType == UnitType.LEVEL_TWO_PAINT_TOWER || myType == UnitType.LEVEL_THREE_PAINT_TOWER);
        boolean isDefenseTower = (myType == UnitType.LEVEL_ONE_DEFENSE_TOWER || myType == UnitType.LEVEL_TWO_DEFENSE_TOWER || myType == UnitType.LEVEL_THREE_DEFENSE_TOWER);

        if (roundNum <= 600) {
            if (splasherBuilt == 0) {
                toBuild = UnitType.SPLASHER;
            } else if (soldierBuilt == 0) {
                toBuild = UnitType.SOLDIER;
            } else {
                int cycleRobots = (soldierBuilt - 1) + (splasherBuilt - 1) + mopperBuilt;
                int cyclePos = cycleRobots % 5; 

                if (cyclePos == 2) {
                    toBuild = UnitType.SPLASHER;
                } else if (cyclePos == 4) {
                    toBuild = UnitType.MOPPER;
                } else {
                    toBuild = UnitType.SOLDIER;
                }
            }
        }
        else {
            if (isMoneyTower) {
                if (soldierBuilt >= mopperBuilt * 2) {
                    toBuild = UnitType.MOPPER;
                } else {
                    toBuild = UnitType.SOLDIER;
                }
            } 
            else if (isPaintTower) {
                if (mopperBuilt >= splasherBuilt) {
                    toBuild = UnitType.SPLASHER;
                } else {
                    toBuild = UnitType.MOPPER;
                }
            } 
            else if (isDefenseTower) {
                if (splasherBuilt >= mopperBuilt) {
                    toBuild = UnitType.MOPPER;
                } else {
                    toBuild = UnitType.SPLASHER;
                }
            }
        }

        RobotInfo[] visibleEnemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (visibleEnemies.length >= 4) {
            toBuild = UnitType.SPLASHER;
        }

        if (toBuild != null) {
            MapLocation myLoc = rc.getLocation();
            
            for (Direction dir : Direction.allDirections()) {
                if (dir != Direction.CENTER) {
                    MapLocation spawnLoc = myLoc.add(dir); 
                    
                    if (rc.canBuildRobot(toBuild, spawnLoc)) {
                        rc.buildRobot(toBuild, spawnLoc);
                        
                        if (toBuild == UnitType.SPLASHER) splasherBuilt++;
                        else if (toBuild == UnitType.SOLDIER) soldierBuilt++;
                        else if (toBuild == UnitType.MOPPER) mopperBuilt++;
                        
                        break; 
                    }
                }
            }
        }
    }
}