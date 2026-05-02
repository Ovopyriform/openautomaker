package org.openautomaker.base.postprocessor.nouveau;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.base.configuration.RoboxProfile;
import org.openautomaker.base.configuration.datafileaccessors.HeadContainer;
import org.openautomaker.base.inject.camera_control.CameraTriggerManagerFactory;
import org.openautomaker.base.postprocessor.CannotCloseFromPerimeterException;
import org.openautomaker.base.postprocessor.GCodeOutputWriter;
import org.openautomaker.base.postprocessor.NoPerimeterToCloseOverException;
import org.openautomaker.base.postprocessor.NotEnoughAvailableExtrusionException;
import org.openautomaker.base.postprocessor.NozzleProxy;
import org.openautomaker.base.postprocessor.PostProcessingError;
import org.openautomaker.base.postprocessor.nouveau.nodes.ExtrusionNode;
import org.openautomaker.base.postprocessor.nouveau.nodes.GCodeEventNode;
import org.openautomaker.base.postprocessor.nouveau.nodes.LayerChangeDirectiveNode;
import org.openautomaker.base.postprocessor.nouveau.nodes.LayerNode;
import org.openautomaker.base.postprocessor.nouveau.nodes.MCodeNode;
import org.openautomaker.base.postprocessor.nouveau.nodes.MergeableWithToolchange;
import org.openautomaker.base.postprocessor.nouveau.nodes.NodeProcessingException;
import org.openautomaker.base.postprocessor.nouveau.nodes.NozzleValvePositionNode;
import org.openautomaker.base.postprocessor.nouveau.nodes.SectionNode;
import org.openautomaker.base.postprocessor.nouveau.nodes.ToolReselectNode;
import org.openautomaker.base.postprocessor.nouveau.nodes.ToolSelectNode;
import org.openautomaker.base.postprocessor.nouveau.nodes.nodeFunctions.IteratorWithStartPoint;
import org.openautomaker.base.postprocessor.nouveau.nodes.providers.Movement;
import org.openautomaker.base.postprocessor.nouveau.nodes.providers.NozzlePositionProvider;
import org.openautomaker.base.services.camera.CameraTriggerData;
import org.openautomaker.base.services.camera.CameraTriggerManager;

import com.google.inject.assistedinject.Assisted;

import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

/**
 *
 * @author Ian
 */
//TODO: So many 'utility' classes.  Need to be refactored
public class UtilityMethods {

	private static final Logger LOGGER = LogManager.getLogger();
	private final PostProcessorFeatureSet ppFeatureSet;
	private final NodeManagementUtilities nodeManagementUtilities;
	private final CloseLogic closeLogic;
	private final RoboxProfile settings;
	private final CameraTriggerManager cameraTriggerManager;
	private final CameraTriggerData cameraTriggerData;

	private final HeadContainer headContainer;

	@Inject
	public UtilityMethods(
			CameraTriggerManagerFactory cameraTriggerManagerFactory,
			HeadContainer headContainer,
			@Assisted PostProcessorFeatureSet ppFeatureSet,
			@Assisted RoboxProfile settings,
			@Assisted String headType,
			@Assisted NodeManagementUtilities nodeManagementUtilities,
			@Nullable @Assisted CameraTriggerData cameraTriggerData) {

		this.headContainer = headContainer;

		this.ppFeatureSet = ppFeatureSet;
		this.settings = settings;
		this.nodeManagementUtilities = nodeManagementUtilities;
		this.closeLogic = new CloseLogic(settings, ppFeatureSet, headType, nodeManagementUtilities);
		this.cameraTriggerManager = cameraTriggerManagerFactory.create(null);
		this.cameraTriggerData = cameraTriggerData;
		cameraTriggerManager.setTriggerData(cameraTriggerData);
	}

	protected void insertCameraTriggersAndCloses(LayerNode layerNode,
			LayerPostProcessResult lastLayerPostProcessResult,
			List<NozzleProxy> nozzleProxies) {
		if (ppFeatureSet.isEnabled(PostProcessorFeature.INSERT_CAMERA_CONTROL_POINTS)) {
			IteratorWithStartPoint<GCodeEventNode> layerForwards = layerNode.treeSpanningIterator(null);
			while (layerForwards.hasNext()) {
				GCodeEventNode layerForwardsEvent = layerForwards.next();

				if (layerForwardsEvent instanceof LayerChangeDirectiveNode) {
					cameraTriggerManager.appendLayerEndTriggerCode((LayerChangeDirectiveNode) layerForwardsEvent);
					break;
				}
			}

			Iterator<GCodeEventNode> layerBackwards = layerNode.childBackwardsIterator();

			if (ppFeatureSet.isEnabled(PostProcessorFeature.OPEN_AND_CLOSE_NOZZLES)) {
				while (layerBackwards.hasNext()) {
					GCodeEventNode layerChild = layerBackwards.next();
					if (layerChild instanceof ToolSelectNode) {
						closeAtEndOfToolSelectIfNecessary((ToolSelectNode) layerChild, nozzleProxies);
						break;
					}
				}
			}
		}
	}

	protected void suppressUnnecessaryToolChangesAndInsertToolchangeCloses(LayerNode layerNode,
			LayerPostProcessResult lastLayerPostProcessResult,
			List<NozzleProxy> nozzleProxies) {
		ToolSelectNode lastToolSelectNode = null;

		if (lastLayerPostProcessResult.getLastToolSelectInForce() != null) {
			lastToolSelectNode = lastLayerPostProcessResult.getLastToolSelectInForce();
		}

		// We know that tool selects come directly under a layer node...        
		Iterator<GCodeEventNode> layerIterator = layerNode.childIterator();

		List<ToolSelectNode> toolSelectNodes = new ArrayList<>();

		while (layerIterator.hasNext()) {
			GCodeEventNode potentialToolSelectNode = layerIterator.next();

			if (potentialToolSelectNode instanceof ToolSelectNode) {
				toolSelectNodes.add((ToolSelectNode) potentialToolSelectNode);
			}
		}

		for (ToolSelectNode toolSelectNode : toolSelectNodes) {
			if (lastToolSelectNode == null) {
				//Our first ever tool select node...
			}
			else if (lastToolSelectNode.getToolNumber() == toolSelectNode.getToolNumber()) {
				toolSelectNode.suppressNodeOutput(true);
			}
			else {
				if (ppFeatureSet.isEnabled(PostProcessorFeature.OPEN_AND_CLOSE_NOZZLES)) {
					closeAtEndOfToolSelectIfNecessary(lastToolSelectNode, nozzleProxies);
				}

				//Now look to see if we can consolidate the tool change with a travel
				if (lastToolSelectNode.getChildren().size() > 0) {
					if (lastToolSelectNode.getChildren().get(lastToolSelectNode.getChildren().size() - 1) instanceof MergeableWithToolchange) {
						((MergeableWithToolchange) lastToolSelectNode.getChildren().get(lastToolSelectNode.getChildren().size() - 1)).changeToolDuringMovement(toolSelectNode.getToolNumber());
						toolSelectNode.suppressNodeOutput(true);
					}
				}
			}

			lastToolSelectNode = toolSelectNode;
		}
	}

	protected void closeAtEndOfToolSelectIfNecessary(ToolSelectNode toolSelectNode, List<NozzleProxy> nozzleProxies) {
		// The tool has changed
		// Close the nozzle if it isn't already...
		//Insert a close at the end if there isn't already a close following the last extrusion
		Iterator<GCodeEventNode> nodeIterator = toolSelectNode.childBackwardsIterator();
		boolean keepLooking = true;
		boolean needToClose = false;
		GCodeEventNode eventToCloseFrom = null;

		List<SectionNode> sectionsToConsiderForClose = new ArrayList<>();

		//If we see a nozzle event BEFORE an extrusion then the nozzle has already been closed
		//If we see an extrusion BEFORE a nozzle event then we must close
		//Keep looking until we find a nozzle event, so that 
		while (nodeIterator.hasNext()
				&& keepLooking) {
			GCodeEventNode node = nodeIterator.next();

			if (node instanceof SectionNode) {
				Iterator<GCodeEventNode> sectionIterator = node.childBackwardsIterator();
				while (sectionIterator.hasNext()
						&& keepLooking) {
					GCodeEventNode sectionChild = sectionIterator.next();
					if (sectionChild instanceof NozzlePositionProvider
							&& ((NozzlePositionProvider) sectionChild).getNozzlePosition().isBSet()) {
						keepLooking = false;
					}
					else if (sectionChild instanceof ExtrusionNode) {
						if (!sectionsToConsiderForClose.contains(node)) {
							sectionsToConsiderForClose.add(0, (SectionNode) node);
						}
						if (eventToCloseFrom == null) {
							eventToCloseFrom = sectionChild;
							needToClose = true;
						}
					}
				}
			}
			else {
				if (node instanceof NozzlePositionProvider
						&& ((NozzlePositionProvider) node).getNozzlePosition().isBSet()) {
					keepLooking = false;
				}
				else if (node instanceof ExtrusionNode) {
					if (eventToCloseFrom == null) {
						eventToCloseFrom = node;
						needToClose = true;
					}
				}
			}
		}

		if (needToClose) {
			try {
				Optional<CloseResult> closeResult = closeLogic.insertProgressiveNozzleClose(eventToCloseFrom, sectionsToConsiderForClose, nozzleProxies.get(toolSelectNode.getToolNumber()));
				if (!closeResult.isPresent()) {
					LOGGER.warn("Close failed - unable to record replenish");
				}
			}
			catch (NodeProcessingException | CannotCloseFromPerimeterException | NoPerimeterToCloseOverException | NotEnoughAvailableExtrusionException | PostProcessingError ex) {
				throw new RuntimeException("Error locating available extrusion during tool select normalisation", ex);
			}
		}
	}

	protected OpenResult insertOpens(LayerNode layerNode,
			OpenResult lastOpenResult,
			List<NozzleProxy> nozzleProxies,
			String headTypeCode) {
		Iterator<GCodeEventNode> layerIterator = layerNode.treeSpanningIterator(null);
		Movement lastMovement = null;
		boolean nozzleOpen = false;
		double lastNozzleValue = 0;
		int lastToolNumber = -1;
		double replenishExtrusionE = 0;
		double replenishExtrusionD = 0;
		int opensInThisTool = 0;

		ExtrusionNode lastNozzleClose = null;
		final float outOfUseNozzleRelief = 5;

		Map<ExtrusionNode, NozzleValvePositionNode> nozzleOpensToAdd = new HashMap<>();
		Map<ExtrusionNode, Integer> toolReselectsToAdd = new HashMap<>();

		if (lastOpenResult != null) {
			nozzleOpen = lastOpenResult.isNozzleOpen();
			replenishExtrusionE = lastOpenResult.getOutstandingEReplenish();
			replenishExtrusionD = lastOpenResult.getOutstandingDReplenish();
			lastToolNumber = lastOpenResult.getLastToolNumber();
			opensInThisTool = lastOpenResult.getOpensInLastTool();
			lastNozzleClose = lastOpenResult.getLastNozzleClose();
		}

		while (layerIterator.hasNext()) {
			GCodeEventNode layerEvent = layerIterator.next();

			if (layerEvent instanceof ToolSelectNode) {
				if (lastToolNumber != ((ToolSelectNode) layerEvent).getToolNumber()) {
					if (lastNozzleClose != null) {
						if (ppFeatureSet.isEnabled(PostProcessorFeature.RETRACT_AT_TOOLCHANGE)) {
							lastNozzleClose.appendCommentText("Adding retract at tool change");
							switch (headContainer.getHeadByID(headTypeCode).nozzles.get(lastToolNumber).associatedExtruder) {
								case "E":
									lastNozzleClose.getExtrusion().setE(-outOfUseNozzleRelief);
									break;
								case "D":
									lastNozzleClose.getExtrusion().setD(-outOfUseNozzleRelief);
									break;
							}
						}

						lastNozzleClose = null;
					}

					lastToolNumber = ((ToolSelectNode) layerEvent).getToolNumber();
					opensInThisTool = 0;
				}
			}
			else if (layerEvent instanceof NozzlePositionProvider
					&& ((NozzlePositionProvider) layerEvent).getNozzlePosition().isPartialOpen()) {
				nozzleOpen = true;
				lastNozzleValue = ((NozzlePositionProvider) layerEvent).getNozzlePosition().getB();

				//As a special case for partials, insert the elided extrusion here
				double replenishEToUse = 0;
				double replenishDToUse = 0;

				switch (headContainer.getHeadByID(headTypeCode).nozzles.get(lastToolNumber).associatedExtruder) {
					case "E":
						replenishEToUse = replenishExtrusionE;
						replenishExtrusionE = 0;
						replenishDToUse = 0;
						break;
					case "D":
						replenishDToUse = replenishExtrusionD;
						replenishExtrusionD = 0;
						replenishEToUse = 0;
						break;
				}

				if (replenishDToUse == 0 && replenishEToUse == 0) {
					String outputString = "No replenish on open in layer " + layerNode.getLayerNumber() + " before partial open " + ((NozzleValvePositionNode) layerEvent).renderForOutput();
					if (layerEvent.getGCodeLineNumber().isPresent()) {
						outputString += " on line " + layerEvent.getGCodeLineNumber().get();
					}
					LOGGER.warn(outputString);
				}

				((NozzleValvePositionNode) layerEvent).setReplenishExtrusionE(replenishEToUse);
				((NozzleValvePositionNode) layerEvent).setReplenishExtrusionD(replenishDToUse);
			}
			else if (layerEvent instanceof NozzlePositionProvider
					&& (((NozzlePositionProvider) layerEvent).getNozzlePosition().isBSet()
							&& ((NozzlePositionProvider) layerEvent).getNozzlePosition().getB() == 1.0)) {
				lastNozzleClose = null;

				nozzleOpen = true;
				lastNozzleValue = ((NozzlePositionProvider) layerEvent).getNozzlePosition().getB();
				switch (headContainer.getHeadByID(headTypeCode).nozzles.get(lastToolNumber).associatedExtruder) {
					case "E":
						replenishExtrusionE = 0;
						break;
					case "D":
						replenishExtrusionD = 0;
						break;
				}

				if (layerEvent instanceof ExtrusionNode) {
					if (lastMovement == null) {
						lastMovement = ((ExtrusionNode) layerEvent).getMovement();
					}
				}
			}
			else if (layerEvent instanceof NozzlePositionProvider
					&& ((NozzlePositionProvider) layerEvent).getNozzlePosition().isBSet()
					&& ((NozzlePositionProvider) layerEvent).getNozzlePosition().getB() < 1.0) {
				if (layerEvent instanceof ExtrusionNode) {
					lastNozzleClose = (ExtrusionNode) layerEvent;
				}

				nozzleOpen = false;
				lastNozzleValue = ((NozzlePositionProvider) layerEvent).getNozzlePosition().getB();
				if (layerEvent instanceof ExtrusionNode) {
					switch (headContainer.getHeadByID(headTypeCode).nozzles.get(lastToolNumber).associatedExtruder) {
						case "E":
							replenishExtrusionE = ((ExtrusionNode) layerEvent).getElidedExtrusion();
							break;
						case "D":
							replenishExtrusionD = ((ExtrusionNode) layerEvent).getElidedExtrusion();
							break;
					}

					if (lastMovement == null) {
						lastMovement = ((ExtrusionNode) layerEvent).getMovement();
					}
				}
			}
			else if (layerEvent instanceof ExtrusionNode
					&& !nozzleOpen) {
				if (lastNozzleValue > 0) {
					String outputString = "Nozzle was not closed properly on layer " + layerNode.getLayerNumber() + " before extrusion " + ((ExtrusionNode) layerEvent).renderForOutput();
					if (layerNode.getGCodeLineNumber().isPresent()) {
						outputString += " on line " + layerNode.getGCodeLineNumber().get();
					}
					LOGGER.warn(outputString);
				}
				NozzleValvePositionNode newNozzleValvePositionNode = new NozzleValvePositionNode();
				if (ppFeatureSet.isEnabled(PostProcessorFeature.RETRACT_AT_TOOLCHANGE)
						&& opensInThisTool == 0) {
					newNozzleValvePositionNode.appendCommentText("Extra replenish - first use after toolchange");
				}
				newNozzleValvePositionNode.getNozzlePosition().setB(1);

				double replenishEToUse = 0;
				double replenishDToUse = 0;

				switch (headContainer.getHeadByID(headTypeCode).nozzles.get(lastToolNumber).associatedExtruder) {
					case "E":
						if (ppFeatureSet.isEnabled(PostProcessorFeature.RETRACT_AT_TOOLCHANGE)
								&& opensInThisTool == 0) {
							replenishExtrusionE += outOfUseNozzleRelief;
						}
						replenishEToUse = replenishExtrusionE;
						replenishExtrusionE = 0;
						replenishDToUse = 0;
						break;
					case "D":
						if (ppFeatureSet.isEnabled(PostProcessorFeature.RETRACT_AT_TOOLCHANGE)
								&& opensInThisTool == 0) {
							replenishExtrusionD += outOfUseNozzleRelief;
						}
						replenishDToUse = replenishExtrusionD;
						replenishExtrusionD = 0;
						replenishEToUse = 0;
						break;
				}

				if (replenishDToUse == 0 && replenishEToUse == 0) {
					String outputString = "No replenish on open in layer " + layerNode.getLayerNumber() + " before extrusion " + ((ExtrusionNode) layerEvent).renderForOutput();
					if (layerEvent.getGCodeLineNumber().isPresent()) {
						outputString += " on line " + layerEvent.getGCodeLineNumber().get();
					}
					LOGGER.warn(outputString);
				}

				newNozzleValvePositionNode.setReplenishExtrusionE(replenishEToUse);
				newNozzleValvePositionNode.setReplenishExtrusionD(replenishDToUse);
				nozzleOpensToAdd.put((ExtrusionNode) layerEvent, newNozzleValvePositionNode);
				nozzleOpen = true;

				opensInThisTool++;
				if (opensInThisTool > settings.getSpecificIntSetting("maxClosesBeforeNozzleReselect")) {
					toolReselectsToAdd.put((ExtrusionNode) layerEvent, lastToolNumber);
					opensInThisTool = 0;
				}

				if (lastMovement == null) {
					lastMovement = ((ExtrusionNode) layerEvent).getMovement();
				}
			}
			//            else if (layerEvent instanceof TravelNode)
			//            {
			//                if (lastMovement == null)
			//                {
			//                    lastMovement = ((TravelNode) layerEvent).getMovement();
			//                } else
			//                {
			//                    Movement thisMovement = ((TravelNode) layerEvent).getMovement();
			//                    Vector2D thisPoint = thisMovement.toVector2D();
			//                    Vector2D lastPoint = lastMovement.toVector2D();
			//
			//                    if (lastPoint.distance(thisPoint) > 5 && !nozzleOpen)
			//                    {
			//                        LOGGER.warning("Travel without close on layer " + layerNode.getLayerNumber() + " at " + ((TravelNode) layerEvent).renderForOutput());
			//                    }
			//                }
			//            }
		}

		nozzleOpensToAdd.entrySet().stream().forEach((entryToUpdate) -> {
			if (toolReselectsToAdd.containsKey(entryToUpdate.getKey())) {
				int toolToReselect = toolReselectsToAdd.get(entryToUpdate.getKey());
				ToolReselectNode reselect = new ToolReselectNode();
				reselect.setToolNumber(toolToReselect);
				reselect.appendCommentText("Reselect nozzle");
				entryToUpdate.getKey().addSiblingBefore(reselect);
			}

			//Add an M109 to make sure temperature is maintained
			MCodeNode tempSustain = new MCodeNode(109);
			entryToUpdate.getKey().addSiblingBefore(tempSustain);
			entryToUpdate.getKey().addSiblingBefore(entryToUpdate.getValue());
		});

		return new OpenResult(replenishExtrusionE, replenishExtrusionD, nozzleOpen, lastToolNumber, opensInThisTool, lastNozzleClose);
	}

	protected void updateLayerToLineNumber(LayerPostProcessResult lastLayerParseResult,
			List<Integer> layerNumberToLineNumber,
			GCodeOutputWriter writer) {
		if (lastLayerParseResult.getLayerData() != null) {
			int layerNumber = lastLayerParseResult.getLayerData().getLayerNumber();
			if (layerNumber >= 0) {
				int nLines = writer.getNumberOfLinesOutput();
				for (int i = layerNumberToLineNumber.size(); i < layerNumber; ++i) {
					LOGGER.warn("Adding missing layer number " + i + " to layer to line number map");
					layerNumberToLineNumber.add(nLines);
				}
				layerNumberToLineNumber.add(layerNumber, nLines);
			}
		}
	}

	public void recalculatePerSectionExtrusion(LayerNode layerNode) {
		Iterator<GCodeEventNode> childrenOfTheLayer = layerNode.childIterator();
		while (childrenOfTheLayer.hasNext()) {
			GCodeEventNode potentialSectionNode = childrenOfTheLayer.next();

			if (potentialSectionNode instanceof SectionNode) {
				((SectionNode) potentialSectionNode).recalculateExtrusion();
			}
		}
	}
}
