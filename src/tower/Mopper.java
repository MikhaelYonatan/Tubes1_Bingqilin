package tower;

import battlecode.common.*;

public class Mopper {

    static int role = -1; 
    
    static MapLocation homeTower = null;
    static MapLocation exploreTarget = null;
    static MapLocation enemyBaseGuess = null;

    static boolean returningPaint = false;
    static boolean firstTurn = true;

    static java.util.Random rng = new java.util.Random();

    public static void runMopper(RobotController rc) throws GameActionException {
        // tentukan peran di turn pertama
        if (role == -1) {
            assignRole(rc);
        }

        // eksekusi berdasarkan peran
        if (role == 0) {
            runGuardLogic(rc);
        } else if (role == 1) {
            runCleanerLogic(rc);
        } else if (role == 2) {
            runDistributorLogic(rc);
        }
    }

    private static void runGuardLogic(RobotController rc) throws GameActionException {  
        // Transfer dulu
        if (firstTurn) {
            if (rc.canTransferPaint(homeTower, 50)) {
                rc.transferPaint(homeTower, 50);
            }
            firstTurn = false;
        }

        // Combat
        if (rc.isActionReady()) {
            RobotInfo[] enemies = rc.senseNearbyRobots(rc.getType().actionRadiusSquared, rc.getTeam().opponent());
            if (enemies.length > 0) {
                boolean hasSwung = false;
                for (Direction dir : Direction.allDirections()) {
                    if (dir != Direction.CENTER && rc.canMopSwing(dir)) {
                        int hitCount = 0;
                        MapLocation myLoc = rc.getLocation();
                        MapLocation[] swingTiles = {
                            myLoc.add(dir), myLoc.add(dir.rotateLeft()), myLoc.add(dir.rotateRight())
                        };

                        for (RobotInfo enemy : enemies) {
                            for (MapLocation tile : swingTiles) {
                                if (enemy.getLocation().equals(tile)) {
                                    hitCount++;
                                    break;
                                }
                            }
                        }

                        if (hitCount >= 2) {
                            rc.mopSwing(dir);
                            hasSwung = true;
                            break; 
                        }
                    }
                }

                if (!hasSwung && rc.canAttack(enemies[0].getLocation())) {
                    rc.attack(enemies[0].getLocation());
                }
            }
        }

        // Transfer lagi
        if (rc.getPaint() >= 90) {
            int amountToTransfer = rc.getPaint() - 55; // Menyisakan tepat 55
            if (rc.getLocation().distanceSquaredTo(homeTower) <= 2) { 
                if (rc.canTransferPaint(homeTower, amountToTransfer)) {
                    rc.transferPaint(homeTower, amountToTransfer);
                }
            } else {
                guardMove(rc, homeTower); 
                return;
            }
        }

        // Jaga tower
        MapLocation greedyTarget = null;
        RobotInfo[] visibleEnemies = rc.senseNearbyRobots(20, rc.getTeam().opponent());
        
        // Prioritas 1: Kejar musuh HANYA JIKA musuh masuk perimeter (dist <= 18)
        for (RobotInfo enemy : visibleEnemies) {
            if (enemy.getLocation().distanceSquaredTo(homeTower) <= 18) {
                greedyTarget = enemy.getLocation();
                break; 
            }
        }
        
        // Cari cat musuh di perimeter
        if (greedyTarget == null) {
            MapInfo[] visibleTiles = rc.senseNearbyMapInfos(rc.getLocation(), 20);
            for (MapInfo tile : visibleTiles) {
                if (tile.getPaint().isEnemy() && tile.getMapLocation().distanceSquaredTo(homeTower) <= 18) {
                    greedyTarget = tile.getMapLocation();
                    break;
                }
            }
        }

        if (greedyTarget != null) {
            guardMove(rc, greedyTarget);
        } else {
            Direction toTower = rc.getLocation().directionTo(homeTower);
            int distToTower = rc.getLocation().distanceSquaredTo(homeTower);
            
            if (toTower == Direction.CENTER) {
                guardMove(rc, rc.getLocation().add(Direction.NORTH));
            } else if (distToTower <= 8) {
                // Jika terlalu dekat dengan tower, jalan menjauh dulu ke pinggiran!
                Direction awayFromTower = toTower.opposite();
                guardMove(rc, rc.getLocation().add(awayFromTower));
            } else {
                // Jika sudah berada di radius ideal (> 8 dan <= 18), putari tower!
                Direction orbitDir = toTower.rotateRight().rotateRight(); 
                MapLocation orbitTarget = rc.getLocation().add(orbitDir).add(orbitDir);
                
                guardMove(rc, orbitTarget);
            }
        }
    }

    private static void runCleanerLogic(RobotController rc) throws GameActionException {
        // Cek cat
        if (rc.getPaint() >= 90) returningPaint = true;
        else if (rc.getPaint() <= 30) returningPaint = false; 

        if (returningPaint) {
            if (rc.getLocation().distanceSquaredTo(homeTower) <= 2) {
                int amountToTransfer = rc.getPaint() - 60; 
                if (rc.canTransferPaint(homeTower, amountToTransfer)) {
                    rc.transferPaint(homeTower, amountToTransfer);
                }
                returningPaint = false; 
            } else {
                Utils.moveToTarget(rc, homeTower);
                return; 
            }
        }

        // Combat
        if (rc.isActionReady()) {
            RobotInfo[] enemies = rc.senseNearbyRobots(rc.getType().actionRadiusSquared, rc.getTeam().opponent());
            
            if (enemies.length > 0) {
                if (rc.canAttack(enemies[0].getLocation())) {
                    rc.attack(enemies[0].getLocation());
                }
            } else {
                MapInfo[] nearbyTiles = rc.senseNearbyMapInfos(rc.getLocation(), rc.getType().actionRadiusSquared);
                for (MapInfo tile : nearbyTiles) {
                    if (tile.getPaint().isEnemy() && rc.canAttack(tile.getMapLocation())) {
                        rc.attack(tile.getMapLocation()); 
                        break; 
                    }
                }
            }
        }
        
        MapLocation greedyTarget = null;
        RobotInfo[] visibleEnemies = rc.senseNearbyRobots(20, rc.getTeam().opponent());
        
        if (visibleEnemies.length > 0) {
            greedyTarget = visibleEnemies[0].getLocation();
        } else {
            MapInfo[] visibleTiles = rc.senseNearbyMapInfos(rc.getLocation(), 20);
            for (MapInfo tile : visibleTiles) {
                if (tile.getPaint().isEnemy()) {
                    greedyTarget = tile.getMapLocation();
                    break;
                }
            }
        }

        // EKSEKUSI PERGERAKAN
        if (greedyTarget != null) {
            Utils.moveToTarget(rc, greedyTarget);
        } else {
            exploreBorder(rc);
        }
    }

    private static void runDistributorLogic(RobotController rc) throws GameActionException {
        // A. COMBAT & CLEANING SAMBIL JALAN
        if (rc.isActionReady()) {
            RobotInfo[] enemies = rc.senseNearbyRobots(rc.getType().actionRadiusSquared, rc.getTeam().opponent());
            if (enemies.length > 0) {
                if (rc.canAttack(enemies[0].getLocation())) rc.attack(enemies[0].getLocation());
            } else {
                MapInfo[] nearbyTiles = rc.senseNearbyMapInfos(rc.getLocation(), rc.getType().actionRadiusSquared);
                for (MapInfo tile : nearbyTiles) {
                    if (tile.getPaint().isEnemy() && rc.canAttack(tile.getMapLocation())) {
                        rc.attack(tile.getMapLocation()); 
                        break; 
                    }
                }
            }
        }

        // B. BACA RADIO & RADAR PENCARI TOWER YANG SUDAH JADI
        MapLocation targetMoneyTower = null;

        // 🔥 1. Dengarkan Laporan Tower Selesai dari Soldier!
        Message[] msgs = rc.readMessages(-1);
        for (Message m : msgs) {
            int msg = m.getBytes();
            if (Utils.decodeHeader(msg) == Utils.MSG_TOWER_DONE) {
                MapLocation knownTower = Utils.decodeLoc(msg);
                
                // Cek apakah tower ini ada di jangkauan pandang kita
                if (rc.canSenseLocation(knownTower)) {
                    RobotInfo towerInfo = rc.senseRobotAtLocation(knownTower);
                    // Validasi apakah ini masih Tower kita dan apakah butuh cat
                    if (towerInfo != null && towerInfo.getTeam() == rc.getTeam() && towerInfo.getPaintAmount() <= 500) {
                        targetMoneyTower = knownTower;
                        break;
                    }
                } else {
                    // Jika belum kelihatan, jadikan waypoint target navigasi agar kita mendekat
                    if (targetMoneyTower == null) targetMoneyTower = knownTower;
                }
            }
        }

        // 🔥 2. Jika tidak ada info dari Radio, cari manual pakai mata (Radar Asli)
        if (targetMoneyTower == null) {
            RobotInfo[] allies = rc.senseNearbyRobots(20, rc.getTeam());
            for (RobotInfo ally : allies) {
                UnitType type = ally.getType();
                if (type == UnitType.LEVEL_ONE_MONEY_TOWER || type == UnitType.LEVEL_TWO_MONEY_TOWER || type == UnitType.LEVEL_THREE_MONEY_TOWER) {
                    
                    if (ally.getPaintAmount() <= 500) {
                        RobotInfo[] towerGuards = rc.senseNearbyRobots(ally.getLocation(), 18, rc.getTeam());
                        int guardCount = 0;
                        for (RobotInfo guard : towerGuards) {
                            if (guard.getType() == UnitType.MOPPER) guardCount++;
                        }

                        if (guardCount < 2) {
                            targetMoneyTower = ally.getLocation();
                            break;
                        }
                    }
                }
            }
        }

        // C. EKSEKUSI TRANSFER ATAU JALAN MENCARI TOWER
        if (targetMoneyTower != null) {
            if (rc.getLocation().distanceSquaredTo(targetMoneyTower) <= 2) {
                RobotInfo destTower = rc.senseRobotAtLocation(targetMoneyTower);
                if (destTower != null && destTower.getPaintAmount() <= 500) {
                    int transferAmount = rc.getPaint() - 51;
                    
                    if (transferAmount > 0 && rc.canTransferPaint(targetMoneyTower, transferAmount)) {
                        rc.transferPaint(targetMoneyTower, transferAmount);
                        
                        homeTower = targetMoneyTower;
                        role = 1;
                        exploreTarget = null; 
                    }
                }
            } else {
                Utils.moveToTarget(rc, targetMoneyTower);
            }
        } else {
            exploreBorder(rc);
        }
    }

    private static void guardMove(RobotController rc, MapLocation target) throws GameActionException {
        if (!rc.isMovementReady() || target == null) return;
        
        Direction bestDir = Direction.CENTER;
        int bestScore = 999999;
        
        for (Direction dir : Direction.allDirections()) {
            if (dir != Direction.CENTER && rc.canMove(dir)) {
                MapLocation nextLoc = rc.getLocation().add(dir);
                
                if (nextLoc.distanceSquaredTo(homeTower) <= 18) {
                    int score = nextLoc.distanceSquaredTo(target);
                    if (score < bestScore) {
                        bestScore = score;
                        bestDir = dir;
                    }
                }
            }
        }
        
        if (bestDir != Direction.CENTER) {
            rc.move(bestDir);
        }
    }

    private static void assignRole(RobotController rc) throws GameActionException {
        int w = rc.getMapWidth();
        int h = rc.getMapHeight();
        
        RobotInfo[] allies = rc.senseNearbyRobots(4, rc.getTeam());
        UnitType spawnerType = null;

        for (RobotInfo ally : allies) {
            if (ally.getType().isTowerType()) {
                homeTower = ally.getLocation();
                spawnerType = ally.getType();
                break;
            }
        }
        
        if (homeTower == null) homeTower = rc.getLocation(); 
        enemyBaseGuess = new MapLocation(w - 1 - homeTower.x, h - 1 - homeTower.y);

        boolean isPaintTower = (spawnerType == UnitType.LEVEL_ONE_PAINT_TOWER || 
                                spawnerType == UnitType.LEVEL_TWO_PAINT_TOWER || 
                                spawnerType == UnitType.LEVEL_THREE_PAINT_TOWER);

        if (rc.getRoundNum() <= 600) {
            if (isPaintTower) {
                role = 2;
            } else {
                role = 1; 
            }
        } else {
            // FASE LATE-GAME (Round 601+)
            boolean isBackline = (homeTower.x < w / 4 || homeTower.x >= 3 * w / 4 || 
                                  homeTower.y < h / 4 || homeTower.y >= 3 * h / 4);

            if (isPaintTower) {
                role = 2;
            } else {
                if (isBackline) {
                    role = 1; 
                } else {
                    RobotInfo[] nearbyGuards = rc.senseNearbyRobots(homeTower, 18, rc.getTeam());
                    int mopperCount = 0;
                    for (RobotInfo bot : nearbyGuards) {
                        if (bot.getType() == UnitType.MOPPER) mopperCount++;
                    }

                    if (mopperCount > 2) {
                        role = 1; 
                    } else {
                        role = 0; 
                    }
                }
            }
        }
    }

    private static void exploreBorder(RobotController rc) throws GameActionException {
        if (exploreTarget == null || rc.getLocation().distanceSquaredTo(exploreTarget) <= 4) {
            int w = rc.getMapWidth();
            int h = rc.getMapHeight();
            
            int minX = 0;
            int maxX = w - 1;
            int minY = 0;
            int maxY = h - 1;

            if (rc.getRoundNum() < 1500) {
                int midX = w / 2;
                int midY = h / 2;
                
                minX = (homeTower.x < midX) ? 0 : midX;
                maxX = (homeTower.x < midX) ? midX - 1 : w - 1;
                
                minY = (homeTower.y < midY) ? 0 : midY;
                maxY = (homeTower.y < midY) ? midY - 1 : h - 1;
            }

            int randX = minX + rng.nextInt(maxX - minX + 1);
            int randY = minY + rng.nextInt(maxY - minY + 1);

            exploreTarget = new MapLocation(randX, randY);
        }

        Utils.moveToTarget(rc, exploreTarget);
    }
}