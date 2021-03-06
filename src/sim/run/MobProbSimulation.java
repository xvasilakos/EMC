package sim.run;

import sim.Scenario;
import app.properties.Space;
import app.properties.valid.Values;
import caching.base.AbstractCachingPolicy;
import caching.interfaces.rplc.IGainRplc;
import exceptions.CriticalFailureException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.SortedMap;
import java.util.logging.Level;
import sim.space.Area;
import sim.space.Point;
import sim.space.cell.CellRegistry;
import sim.space.cell.smallcell.SmallCell;
import sim.space.users.mobile.MobileUser;
import sim.time.NormalSimulationEndException;
import sim.space.users.StationaryUser;
import sim.space.users.mobile.MUBuilder;
import sim.space.users.mobile.MobileGroup;
import sim.space.users.mobile.MobileGroupsRegistry;
import statistics.handlers.iterative.sc.cmpt6.UnonymousCompute6;

/**
 *
 * @author Xenofon Vasilakos xvas@aueb.gr
 */
public final class MobProbSimulation extends SimulationBaseRunner<MobileUser> {

    public MobProbSimulation(Scenario s) {
        super(s);
    }

    @Override
    protected void constructorInit(Scenario setup) {
        // nothing special to do here that is not done by the super constructor
    }

    /**
     * Initializes mobile users on the setArea.
     *
     * @param scenario
     * @param ugReg
     * @param area
     * @param scReg
     * @param cachingPolicies
     * @return
     */
    @Override
    protected List<MobileUser> initAndConnectMUs(
            Scenario scenario, MobileGroupsRegistry ugReg,
            Area area, CellRegistry scReg,
            Collection<AbstractCachingPolicy> cachingPolicies) {

        List<MobileUser> musLst = new ArrayList<>();

        List<String> conn2SCPolicy;

        conn2SCPolicy = scenario.parseConnPolicySC();
        String mobTransDecisions = scenario.stringProperty(Space.MU__TRANSITION_DECISIONS, false);
        int itemRndIDRange = scenario.intProperty(Space.ITEM__RND_ID_RANGE);
        double percentage = scenario.doubleProperty(app.properties.Simulation.PROGRESS_UPDATE);

        //<editor-fold defaultstate="collapsed" desc="for each group of MUs {">
        // note that registeredGroups are sorted after their setId.
        SortedMap<Integer, MobileGroup> groupsMap = ugReg.registeredGroups();
        Iterator<Integer> groupIDIter = groupsMap.keySet().iterator();
        while (groupIDIter.hasNext()) {

            MobileGroup nxtGroup = groupsMap.get(groupIDIter.next());
            int musNum = nxtGroup.getSize();

            //</editor-fold>
            //<editor-fold defaultstate="collapsed" desc="report/log progress">
            _logger.log(Level.INFO, "Initializing MUs on the area:\n\t{0}/{1}", new Object[]{0, musNum});
            int count = 0;
            int printPer = (int) (musNum * percentage);
            printPer = printPer == 0 ? 1 : printPer; // otherwise causes arithmetic exception devide by zero in some cases
            //</editor-fold>

            for (int i = 1; i <= musNum; i++) {

                int nxtMuID = ugReg.generateMobileID(nxtGroup.getId(), i);

                double[] probs = nxtGroup.transProbsForMember(i);

                //<editor-fold defaultstate="collapsed" desc="nxtMUBuilder">
                Point startPoint = area.getPoint(nxtGroup.getInitPos());

                MUBuilder nxtMUBuilder = new MUBuilder(
                        this, nxtGroup, startPoint, probs,
                        conn2SCPolicy, cachingPolicies
                );

                nxtMUBuilder.setId(nxtMuID);

                nxtMUBuilder.setArea(area);

                nxtMUBuilder.setTransitionDecisions(mobTransDecisions);

                int tmp;
                if (itemRndIDRange > 1) {
                    tmp = (int) (getRandomGenerator().randIntInRange(1, itemRndIDRange));
                } else {
                    tmp = nxtMuID;
                }

                MobileUser mu = nxtMUBuilder.build();
                //</editor-fold>
                musLst.add(mu);

                //<editor-fold defaultstate="collapsed" desc="report/log progress">
                if (++count % printPer == 0) {
                    _logger.log(Level.INFO, "\tMobiles prepared:{0}%", Math.round(100.0 * count / musNum) / 100);
                }
                //</editor-fold> 
            }//for every MU__CLASS in group
        }

        //<editor-fold defaultstate="collapsed" desc="shuffle mus iff property ..">
        String muShuffling = scenario.stringProperty(Space.MU__SHUFFLE, false);
        switch (muShuffling) {
            case Values.NEVER:
                break; // do not shufle

            case Values.UPON_CREATION:
            case Values.ALWAYS:
                Collections.shuffle(musLst, getRandomGenerator().getMersenneTwister());
                break;
            default:
                throw new UnsupportedOperationException("Value " + muShuffling + " is currently not supported for "
                        + " property " + Space.MU__SHUFFLE);
        }
        //</editor-fold>
        return musLst;
    }

    @Override
    @SuppressWarnings("empty-statement")
    public void run() {
        try { // out-of-while try: catches all types of exceptions

            while (!Thread.currentThread().isInterrupted()
                    && isDuringWarmupPeriod(getTrcLoader())) {
                _clock.tick();
            };

            /*
             * if warmup period has passed..
             */
            WHILE_THREAD_NOT_INTERUPTED:
            while (!Thread.currentThread().isInterrupted()) {
                _clock.tick();

//////////////////////////////////////////////////                
//yyy                runGoldenRatioSearchEMPCLC();
//////////////////////////////////////////////////
                if (stationaryRequestsUsed()) {/*
                     * Concume data and keep gain stats for stationary users
                     */
                    for (SmallCell nxtSC : smallCells()) {
                        StationaryUser nxtSU = nxtSC.getStationaryUser();
                        nxtSC.updtLclDmdByStationary(false);
                        nxtSU.consumeDataTry(1);
                        nxtSU.tryCacheRecentFromBH();// try to cache whatever not already in the cache that you just downloaded.
                    }
                }

/////////////////////////////////////
                List<MobileUser> shuffldMUs = shuffledMUs();
                _haveExitedPrevCell.clear();
                getStatsHandle().resetHandoverscount();

                for (MobileUser nxtMU : shuffldMUs) {
                    nxtMU.move(false, false);
                    if (nxtMU.isSoftUser()) {
                        nxtMU.consumeTryAllAtOnceFromSC();
                    } else {
                        nxtMU.consumeDataTry(1);// consume in one simulation time step
                    }
                }// for all all MUs

                getStatsHandle().statHandoversCount();
/////////////////////////////////////

                for (AbstractCachingPolicy nxtPolicy : _cachingPolicies) {/*
                     * update priority queues of cached chunks for each
                     * IGainRplc replacement policy, in every small cell.
                     */
                    if (!(nxtPolicy instanceof IGainRplc)) {
                        continue;
                    }
                    IGainRplc gainRplcPolicy = (IGainRplc) nxtPolicy;
                    for (SmallCell sc : smallCells()) {
                        sc.updtCachedChunksOrderedByGain(gainRplcPolicy);
                    }
                }

///////////////////////////////////////////////// 
                int clearedReqs = 0;
                int newAddedReqs = 0;
                for (MobileUser nxtMU : _haveExitedPrevCell) {
                    SmallCell lastSCForCacheDecisions = nxtMU.getLastSCForCacheDecisions();
                    if (lastSCForCacheDecisions != null) {
                        getStatsHandle().updtPerformanceStats(nxtMU);
                        // cancel past PC decisions
                        nxtMU.cancelAndDeregisterPCOrders();
                    }

                    if (usesTraceOfRequests()) {
                        if (nxtMU.isSoftUser()) {
                            clearedReqs += nxtMU.clearAllRequests();
                        }
                        clearedReqs += nxtMU.clearCompletedRequests();
                        newAddedReqs += updtLoadWorkloadRequests(nxtMU, _dmdTrcReqsLoadedPerUser);
                    }

                    // finaly take caching decisions
                    nxtMU.cacheDescisionsPerformRegisterPC(nxtMU.getLastKnownConnectedSC());
                }

                getSim().getStatsHandle().updtSCCmpt6(clearedReqs,
                        new UnonymousCompute6(
                                new UnonymousCompute6.WellKnownTitle("ClearedReqs"))
                );
                getSim().getStatsHandle().updtSCCmpt6(newAddedReqs,
                        new UnonymousCompute6(
                                new UnonymousCompute6.WellKnownTitle("newAddedReqs"))
                );

////////////////////////////////////////////////////
                boolean roundCommited = runUpdtStats4SimRound();
                if (roundCommited) {
                    getStatsHandle().appendTransient(false);
                    getStatsHandle().checkFlushTransient(false);
                }
            }// while simulation continues// while simulation continues// while simulation continues// while simulation continues

        } catch (NormalSimulationEndException simEndEx) {
            _logger.log(
                    Level.INFO, "Simulation {0} ended: {1}",
                    new Object[]{
                        Thread.currentThread().getName(),
                        simEndEx.getMessage()
                    });
        } catch (Throwable ex) {
            _logger.log(Level.SEVERE, "Simulation " + getID()
                    + " terminates unsuccessfully at time " + simTime(),
                    new CriticalFailureException(ex));
        } finally {
            runFinish();
        }
    }

}
