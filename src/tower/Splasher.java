package tower;

import battlecode.common.*;

public class Splasher {

    static MapLocation homeLoc = null;
    static MapLocation enemyBaseGuess = null;
    static MapLocation exploreTarget = null;

    static boolean enemyBaseDestroyed = false;
    
    static MapLocation targetRuin = null; 
    static MapLocation[] ignoredRuinsList = new MapLocation[10];
    static int ignoredIndex = 0;

    static boolean nextFrontlineIsDefense = true;
    static boolean nextBacklineIsPaint = true;
    
    static java.util.Random rng = new java.util.Random(); 

    public static void runSplasher(RobotController rc) throws GameActionException {
        boolean hasBroadcastedThisTurn = false;

        // 0. AUTO-UPGRADE
        Utils.tryUpgradeTowers(rc);

        // 1. INISIALISASI TARGET
        if (exploreTarget == null) {
            initTarget(rc);
        }

        // 2. GREEDY COMBAT
        if (rc.getPaint() < 50) {
            Utils.retreatToPaintTower(rc, homeLoc);
            return;
        }

        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        for (RobotInfo enemy : enemies) {
            if (enemy.getType().isTowerType()) {
                if (!hasBroadcastedThisTurn) {
                    Utils.sendRadio(rc, Utils.MSG_ENEMY_TOWER, enemy.getLocation());
                    hasBroadcastedThisTurn = true;
                }

                if (rc.canAttack(enemy.getLocation()) && rc.isActionReady()) {
                    rc.attack(enemy.getLocation()); 
                }
                Utils.moveToTarget(rc, enemy.getLocation());
                return; 
            }
        }

        if (rc.isActionReady()) {
            if (enemies.length > 0) {
                MapLocation enemyLoc = enemies[0].getLocation();
                if (rc.canAttack(enemyLoc) && rc.getPaint() >= 100) {
                    rc.attack(enemyLoc);
                }
            }

            if (rc.getPaint() >= 50 && rc.getPaint() <= 60) {
                int roundNum = rc.getRoundNum(); 
                
                MapInfo[] splashableTiles = rc.senseNearbyMapInfos(rc.getLocation(), rc.getType().actionRadiusSquared);
                MapLocation bestTarget = null;
                boolean foundEnemyTarget = false;

                for (MapInfo tile : splashableTiles) {
                    MapLocation targetLoc = tile.getMapLocation();
                    if (!rc.canAttack(targetLoc)) continue;

                    boolean isSafeToSplash = true;
                    boolean hitsEnemyArea = false;

                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            MapLocation splashLoc = new MapLocation(targetLoc.x + dx, targetLoc.y + dy);
                            
                            if (!rc.canSenseLocation(splashLoc)) continue;

                            MapInfo splashInfo = rc.senseMapInfo(splashLoc);
                            RobotInfo occ = rc.senseRobotAtLocation(splashLoc);

                            if (splashInfo.hasRuin()) {
                                if (occ == null || occ.getTeam() != rc.getTeam().opponent()) {
                                    isSafeToSplash = false; break;
                                }
                            }

                            if (roundNum < 700 && splashInfo.getMark() != PaintType.EMPTY) {
                                isSafeToSplash = false; break;
                            }

                            if (splashInfo.getPaint().isEnemy() || (occ != null && occ.getTeam() == rc.getTeam().opponent())) {
                                hitsEnemyArea = true;
                            }
                        }
                        if (!isSafeToSplash) break; 
                    }

                    if (!isSafeToSplash) continue;

                    if (hitsEnemyArea) {
                        bestTarget = targetLoc;
                        foundEnemyTarget = true;
                        break; 
                    }
                    
                    if (!foundEnemyTarget && bestTarget == null && tile.getPaint() == PaintType.EMPTY) {
                        if (rc.getLocation().distanceSquaredTo(targetLoc) > 2) {
                            bestTarget = targetLoc;
                        }
                    }
                }

                if (bestTarget != null) {
                    rc.attack(bestTarget);
                    return; 
                }
            }
        }

        // 2.5 MARK RESOURCE PATTERN (EARLY GAME)
        if (rc.getRoundNum() <= 300) {
            MapLocation myLoc = rc.getLocation();
            boolean conflict = false;
            
            // Periksa area sekitar (Radius 34 mencakup jarak Chebyshev hingga 5 petak)
            MapInfo[] scanResource = rc.senseNearbyMapInfos(myLoc, 34); 
            for (MapInfo tile : scanResource) {
                MapLocation loc = tile.getMapLocation();
                int dx = Math.abs(loc.x - myLoc.x);
                int dy = Math.abs(loc.y - myLoc.y);
                
                // Mencegah bentrok dengan 5x5 Tower (jarak aman minimal 5 petak)
                if (tile.hasRuin() && Math.max(dx, dy) <= 4) {
                    conflict = true;
                    break;
                }
                // Mencegah tumpang tindih dengan mark lain di area 5x5 resource ini
                if (Math.max(dx, dy) <= 2 && tile.getMark() != PaintType.EMPTY) {
                    conflict = true;
                    break;
                }
            }

            if (!conflict) {
                try {
                    rc.markResourcePattern(myLoc);
                } catch (Exception e) {} // Hindari error jika cooldown atau syarat game menahan
            }
        }

        // 3. BACA RADIO
        Message[] msgs = rc.readMessages(-1);
        for (Message m : msgs) {
            int msg = m.getBytes();
            int header = Utils.decodeHeader(msg);
            MapLocation loc = Utils.decodeLoc(msg);

            if (header == Utils.MSG_TOWER_DONE) {
                ignoreRuin(loc);
                if (targetRuin != null && targetRuin.equals(loc)) targetRuin = null;
            } 
            else if (header == Utils.MSG_ENEMY_TOWER) {
                exploreTarget = loc;
                enemyBaseDestroyed = false; 
            }
        }

        // 4. GREEDY RUIN
        if (targetRuin == null) {
            MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
            for (MapInfo tile : nearbyTiles) {
                MapLocation loc = tile.getMapLocation();
                
                if (tile.hasRuin() && !isRuinIgnored(loc)) {
                    if (!hasBroadcastedThisTurn) {
                        Utils.sendRadio(rc, Utils.MSG_RUIN_FOUND, loc);
                        hasBroadcastedThisTurn = true;
                    }

                    boolean alreadyMarked = false;
                    MapInfo[] scanMarks = rc.senseNearbyMapInfos(loc, 8);
                    for (MapInfo scanTile : scanMarks) {
                        if (!scanTile.getMapLocation().equals(loc) && scanTile.getMark() != PaintType.EMPTY) {
                            alreadyMarked = true;
                            break;
                        }
                    }

                    if (alreadyMarked) {
                        ignoreRuin(loc);
                        continue; 
                    }

                    UnitType typeToMark = determineMarkType(rc, loc);
                    
                    if (typeToMark != null) {
                        targetRuin = loc;
                        break; 
                    } else {
                        ignoreRuin(loc);
                        decideNextMoves(rc);
                    }
                }
            }
        }

        // 5. EKSEKUSI RUIN
        if (targetRuin != null) {
            if (rc.getLocation().distanceSquaredTo(targetRuin) > 2) {
                Utils.moveToTarget(rc, targetRuin);
                return;
            }
            
            boolean completed = false;
            UnitType[] towerTypes = {UnitType.LEVEL_ONE_MONEY_TOWER, UnitType.LEVEL_ONE_PAINT_TOWER, UnitType.LEVEL_ONE_DEFENSE_TOWER};
            for (UnitType t : towerTypes) {
                if (rc.canCompleteTowerPattern(t, targetRuin)) {
                    rc.completeTowerPattern(t, targetRuin);
                    
                    if (!hasBroadcastedThisTurn) {
                        Utils.sendRadio(rc, Utils.MSG_TOWER_DONE, targetRuin);
                        hasBroadcastedThisTurn = true;
                    }
                    
                    completed = true;
                    break;
                }
            }

            if (completed) {
                ignoreRuin(targetRuin); 
                targetRuin = null; 
                decideNextMoves(rc);
                return;
            }

            boolean hasMark = false;
            MapInfo[] scanMarks = rc.senseNearbyMapInfos(targetRuin, 8);
            for (MapInfo scanTile : scanMarks) {
                if (!scanTile.getMapLocation().equals(targetRuin) && scanTile.getMark() != PaintType.EMPTY) {
                    hasMark = true;
                    break;
                }
            }

            if (hasMark) {
                ignoreRuin(targetRuin);
                targetRuin = null;
                decideNextMoves(rc);
                return;
            }

            UnitType typeToMark = determineMarkType(rc, targetRuin);
            
            if (typeToMark != null && rc.canMarkTowerPattern(typeToMark, targetRuin)) {
                rc.markTowerPattern(typeToMark, targetRuin);
                
                if (!hasBroadcastedThisTurn) {
                    Utils.sendRadio(rc, Utils.MSG_RUIN_FOUND, targetRuin);
                    hasBroadcastedThisTurn = true;
                }

                RobotInfo occ = rc.senseRobotAtLocation(targetRuin);
                if (occ == null || occ.getTeam() != rc.getTeam()) {
                    int distToHome = targetRuin.distanceSquaredTo(homeLoc);
                    int distToEnemy = targetRuin.distanceSquaredTo(enemyBaseGuess);
                    boolean isFrontline = distToHome > distToEnemy;

                    if (isFrontline) {
                        nextFrontlineIsDefense = !nextFrontlineIsDefense;
                    } else {
                        nextBacklineIsPaint = !nextBacklineIsPaint;
                    }
                }
            }
            
            ignoreRuin(targetRuin); 
            targetRuin = null; 
            decideNextMoves(rc);
            return;
        }

        // 6. EKSPLORASI
        if (rc.getLocation().distanceSquaredTo(exploreTarget) <= 9) {
            decideNextMoves(rc);
        }

        Utils.moveToTarget(rc, exploreTarget);
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
                exploreTarget = new MapLocation(hx, ey);
            } else {
                exploreTarget = new MapLocation(ex, hy);
            }
        }
    }

    private static void decideNextMoves(RobotController rc) throws GameActionException {
        boolean isInvaderRole = (rc.getID() % 3 != 0); 
        boolean hasEnoughPaint = rc.getPaint() >= 150; 
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
            
            randX = Math.max(0, Math.min(w - 1, randX));
            randY = Math.max(0, Math.min(h - 1, randY));
            
            exploreTarget = new MapLocation(randX, randY);
        }
    }

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

    private static UnitType determineMarkType(RobotController rc, MapLocation ruinLoc) throws GameActionException {
        RobotInfo occupant = rc.senseRobotAtLocation(ruinLoc);

        if (occupant != null && occupant.getTeam() == rc.getTeam()) {
            return occupant.getType(); 
        } 
        
        if (occupant == null || occupant.getTeam() != rc.getTeam()) {
            int distToHome = ruinLoc.distanceSquaredTo(homeLoc);
            int distToEnemy = ruinLoc.distanceSquaredTo(enemyBaseGuess);
            boolean isFrontline = distToHome > distToEnemy; 
            
            if (isFrontline) {
                return nextFrontlineIsDefense ? UnitType.LEVEL_ONE_DEFENSE_TOWER : UnitType.LEVEL_ONE_PAINT_TOWER;
            } else {
                return nextBacklineIsPaint ? UnitType.LEVEL_ONE_PAINT_TOWER : UnitType.LEVEL_ONE_MONEY_TOWER;
            }
        }
        
        return null; 
    }
}