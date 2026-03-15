package tower;

import battlecode.common.*;

public class Soldier {

    static MapLocation homeLoc = null;
    static MapLocation enemyBaseGuess = null;
    static MapLocation exploreTarget = null;

    static boolean enemyBaseDestroyed = false;
    
    static MapLocation targetRuin = null; 
    
    static MapLocation[] ignoredRuinsList = new MapLocation[10];
    static int ignoredIndex = 0;

    static java.util.Random rng = new java.util.Random(); 

    public static void runSoldier(RobotController rc) throws GameActionException {
        // 0. auto-upgrade jika ada uang dan lewat dekat tower (sinergi utils)
        Utils.tryUpgradeTowers(rc);

        // 1. inisialisasi target awal (formasi waypoint rush)
        if (exploreTarget == null) {
            initTarget(rc);
        }

        // 2. kondisi darurat
        if (rc.getPaint() < 40) {
            
            if (rc.isActionReady()) {
                MapInfo[] nearbyTiles = rc.senseNearbyMapInfos(rc.getLocation(), rc.getType().actionRadiusSquared);
                for (MapInfo tile : nearbyTiles) {
                    if (!tile.getPaint().isAlly() && rc.canAttack(tile.getMapLocation())) {
                        rc.attack(tile.getMapLocation());
                        break;
                    }
                }
            }
            
            return; 
        }

        // 3. greedy combat (hanya serang tower musuh)
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        for (RobotInfo enemy : enemies) {
            if (enemy.getType().isTowerType()) {
                if (rc.canAttack(enemy.getLocation()) && rc.isActionReady()) {
                    rc.attack(enemy.getLocation()); 
                }
                Utils.moveToTarget(rc, enemy.getLocation());
                return; 
            }
        }

        MapLocation unpaintedMarkTarget = null;

        // baca radio & gossip protocol (mesh network)
        Message[] msgs = rc.readMessages(-1);
        for (Message m : msgs) {
            int msg = m.getBytes();
            int header = Utils.decodeHeader(msg);
            MapLocation loc = Utils.decodeLoc(msg);

            if (header == Utils.MSG_RUIN_FOUND && !isRuinIgnored(loc)) {
                if (targetRuin == null || rc.getLocation().distanceSquaredTo(loc) < rc.getLocation().distanceSquaredTo(targetRuin)) {
                    targetRuin = loc; 
                }
            } else if (header == Utils.MSG_TOWER_DONE) {
                ignoreRuin(loc); 
                if (targetRuin != null && targetRuin.equals(loc)) targetRuin = null; 
            } else if (header == Utils.MSG_ENEMY_TOWER) {
                exploreTarget = loc;
                enemyBaseDestroyed = false; 
            }
        }

        // 4. mencari ruin, tower, atau mark untuk dibangun/diekstensi
        if (targetRuin == null) {
            MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
            
            MapLocation foundEmptyRuin = null;
            MapLocation foundMark = null;
            MapLocation fallbackAllyTower = null;

            boolean hasBroadcastedThisTurn = false;

            for (MapInfo tile : nearbyTiles) {
                MapLocation loc = tile.getMapLocation();
                
                if (tile.hasRuin()) {
                    RobotInfo occ = rc.canSenseLocation(loc) ? rc.senseRobotAtLocation(loc) : null;
                    boolean isEnemyTower = (occ != null && occ.getTeam() == rc.getTeam().opponent() && occ.getType().isTowerType());
                    
                    if (isEnemyTower && !hasBroadcastedThisTurn) {
                        Utils.sendRadio(rc, Utils.MSG_ENEMY_TOWER, loc);
                        hasBroadcastedThisTurn = true;
                    } 
                    else if (!hasBroadcastedThisTurn) {
                        Utils.sendRadio(rc, Utils.MSG_RUIN_FOUND, loc);
                        hasBroadcastedThisTurn = true;
                    }

                    if (!isRuinIgnored(loc)) {
                        boolean isAllyTower = (occ != null && occ.getTeam() == rc.getTeam() && occ.getType().isTowerType());
                        
                        if (!isAllyTower && foundEmptyRuin == null) {
                            foundEmptyRuin = loc; 
                        } else if (isAllyTower && fallbackAllyTower == null) {
                            fallbackAllyTower = loc; 
                        }
                    }
                }
                
                if (foundMark == null) {
                    PaintType mark = tile.getMark();
                    if (mark != PaintType.EMPTY && mark != tile.getPaint()) {
                        boolean isMarkIgnored = false;
                        for (MapLocation ignoredRuin : ignoredRuinsList) {
                            if (ignoredRuin != null && ignoredRuin.distanceSquaredTo(loc) <= 8) {
                                isMarkIgnored = true;
                                break;
                            }
                        }
                        if (!isMarkIgnored) {
                            foundMark = loc;
                        }
                    }
                }
            }

            if (foundEmptyRuin != null) {
                targetRuin = foundEmptyRuin;
            } 
            else if (foundMark != null) {
                MapLocation ownerRuin = null;
                for (MapInfo t : nearbyTiles) {
                    if (t.hasRuin() && t.getMapLocation().distanceSquaredTo(foundMark) <= 8) {
                        ownerRuin = t.getMapLocation();
                        break;
                    }
                }

                if (ownerRuin != null) {
                    targetRuin = ownerRuin;
                } else {
                    unpaintedMarkTarget = foundMark;
                }
            } 
            else if (fallbackAllyTower != null) {
                targetRuin = fallbackAllyTower;
            }
        }

        if (targetRuin == null && unpaintedMarkTarget != null) {
            Utils.moveToTarget(rc, unpaintedMarkTarget);
            return;
        }

        if (targetRuin != null) {
            if (rc.getLocation().distanceSquaredTo(targetRuin) > 8) {
                Utils.moveToTarget(rc, targetRuin);
                return;
            }

            boolean completed = false;
            UnitType[] towerTypes = {UnitType.LEVEL_ONE_MONEY_TOWER, UnitType.LEVEL_ONE_PAINT_TOWER, UnitType.LEVEL_ONE_DEFENSE_TOWER};
            for (UnitType t : towerTypes) {
                if (rc.canCompleteTowerPattern(t, targetRuin)) {
                    rc.completeTowerPattern(t, targetRuin);

                    Utils.sendRadio(rc, Utils.MSG_TOWER_DONE, targetRuin);
                    ignoreRuin(targetRuin); 
                    targetRuin = null;
                    
                    completed = true;
                    break;
                }
            }
            if (completed) return;

            RobotInfo centerRobot = rc.canSenseLocation(targetRuin) ? rc.senseRobotAtLocation(targetRuin) : null;
            boolean isExistingTower = (centerRobot != null && centerRobot.getTeam() == rc.getTeam());

            MapInfo[] visibleTiles = rc.senseNearbyMapInfos(targetRuin, 18); 
            
            MapLocation priority1Target = null;
            boolean priority1Secondary = false;
            
            MapLocation priority2Target = null; 

            for (MapInfo info : visibleTiles) {
                MapLocation loc = info.getMapLocation();
                int dx = Math.abs(loc.x - targetRuin.x);
                int dy = Math.abs(loc.y - targetRuin.y);
                
                if (dx == 0 && dy == 0) continue; // Abaikan pusat

                // --- CEK PRIORITAS 1: Area 5x5 ---
                if (dx <= 2 && dy <= 2) {
                    boolean needsPaint = false;
                    boolean requiresSecondary = false;

                    if (isExistingTower) {
                        if (!info.getPaint().isAlly()) {
                            if (!info.getPaint().isEnemy()) {
                                needsPaint = true;
                            }
                        }
                    } else {
                        if (info.getMark() != PaintType.EMPTY && info.getMark() != info.getPaint()) {
                            if (!info.getPaint().isEnemy()) {
                                needsPaint = true;
                                requiresSecondary = (info.getMark() == PaintType.ALLY_SECONDARY);
                            }
                        }
                    }

                    if (needsPaint && priority1Target == null) {
                        if (rc.canAttack(loc)) {
                            priority1Target = loc;
                            priority1Secondary = requiresSecondary;
                        } else if (priority1Target == null) {
                            priority1Target = loc; 
                            priority1Secondary = requiresSecondary;
                        }
                    }
                } 
                else if (info.getMark() == PaintType.EMPTY && !info.getPaint().isAlly() && !info.getPaint().isEnemy()) {
                    boolean adjacentToMark = false;
                    for (MapInfo otherTile : visibleTiles) {
                        if (otherTile.getMark() != PaintType.EMPTY) {
                            if (loc.isAdjacentTo(otherTile.getMapLocation())) {
                                adjacentToMark = true;
                                break;
                            }
                        }
                    }

                    if (adjacentToMark) {
                        if (rc.canAttack(loc)) {
                            priority2Target = loc; 
                        } else if (priority2Target == null) {
                            priority2Target = loc;
                        }
                    }
                }
            }

            MapLocation finalTarget = null;
            boolean finalSecondary = false;

            if (priority1Target != null) {
                finalTarget = priority1Target;
                finalSecondary = priority1Secondary;
            } else if (priority2Target != null) {
                finalTarget = priority2Target;
                finalSecondary = false; 
            }

            if (finalTarget != null) {
                if (rc.canAttack(finalTarget) && rc.isActionReady() && rc.getPaint() >= 10) {
                    rc.attack(finalTarget, finalSecondary);
                } else if (!rc.canAttack(finalTarget) && rc.isMovementReady()) {
                    Utils.moveToTarget(rc, finalTarget);
                }
                return;
            } 

            else {
                Utils.sendRadio(rc, Utils.MSG_TOWER_DONE, targetRuin); 
                
                ignoreRuin(targetRuin);
                targetRuin = null;
                decideNextMoves(rc);
                return;
            }
        }

        if (rc.getLocation().distanceSquaredTo(exploreTarget) <= 9) {
            decideNextMoves(rc);
        }

        Utils.moveToTarget(rc, exploreTarget);
    }

    // --- helper methods ---
    private static void ignoreRuin(MapLocation loc) {
        ignoredRuinsList[ignoredIndex % 10] = loc;
        ignoredIndex++;
    }

    private static boolean isRuinIgnored(MapLocation loc) {
        for (int i = 0; i < 10; i++) {
            if (ignoredRuinsList[i] != null && ignoredRuinsList[i].equals(loc)) {
                return true;
            }
        }
        return false;
    }

    private static void initTarget(RobotController rc) {
        int width = rc.getMapWidth();
        int height = rc.getMapHeight();
        homeLoc = rc.getLocation();
        
        enemyBaseGuess = new MapLocation(width - 1 - homeLoc.x, height - 1 - homeLoc.y);
        MapLocation centerTarget = new MapLocation(width / 2, height / 2);
        
        int lane = rc.getID() % 3;
        
        if (lane != 0) {
            int randomX = rng.nextInt(9) - 4; 
            int randomY = rng.nextInt(9) - 4;
            exploreTarget = new MapLocation(
                Math.max(0, Math.min(width - 1, centerTarget.x + randomX)), 
                Math.max(0, Math.min(height - 1, centerTarget.y + randomY))
            );
        } else {
            boolean isWing1 = rng.nextBoolean();
            
            int ex = (enemyBaseGuess.x > width / 2) ? width - 1 : 0;
            int ey = (enemyBaseGuess.y > height / 2) ? height - 1 : 0;
            int hx = (homeLoc.x > width / 2) ? width - 1 : 0;
            int hy = (homeLoc.y > height / 2) ? height - 1 : 0;
            
            if (isWing1) {
                exploreTarget = new MapLocation(hx, ey); // Sayap 1
            } else {
                exploreTarget = new MapLocation(ex, hy); // Sayap 2
            }
        }
    }

    private static void decideNextMoves(RobotController rc) throws GameActionException {
        boolean isInvaderRole = (rc.getID() % 3 != 0); 
        boolean hasEnoughPaint = rc.getPaint() >= 40; 
        
        int w = rc.getMapWidth();
        int h = rc.getMapHeight();

        if (!enemyBaseDestroyed && rc.canSenseLocation(enemyBaseGuess)) {
            RobotInfo occ = rc.senseRobotAtLocation(enemyBaseGuess);
            if (occ == null || occ.getTeam() != rc.getTeam().opponent() || !occ.getType().isTowerType()) {
                enemyBaseDestroyed = true;
            }
        }

        if (isInvaderRole && hasEnoughPaint) {
            if (!enemyBaseDestroyed) {
                exploreTarget = enemyBaseGuess;
            } else {
                int ex = (enemyBaseGuess.x > w / 2) ? w - 1 : 0;
                int ey = (enemyBaseGuess.y > h / 2) ? h - 1 : 0;
                int hx = (homeLoc.x > w / 2) ? w - 1 : 0;
                
                int sweepChoice = rng.nextInt(3);
                if (sweepChoice == 0) {
                    exploreTarget = new MapLocation(ex, ey);
                } else if (sweepChoice == 1) {
                    exploreTarget = new MapLocation(hx, ey);
                } else {
                    exploreTarget = new MapLocation(ex, h / 2);
                }
            }
        } else {
            int midX = (homeLoc.x + (w / 2)) / 2;
            int midY = (homeLoc.y + (h / 2)) / 2;
            
            int randX = midX + (rng.nextInt(w / 3) - w / 6);
            int randY = midY + (rng.nextInt(h / 3) - h / 6);
            
            exploreTarget = new MapLocation(Math.max(0, Math.min(w - 1, randX)), Math.max(0, Math.min(h - 1, randY)));
        }
    }
}