/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jface.text.projection;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.text.AbstractDocument;
import org.eclipse.jface.text.Assert;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.BadPositionCategoryException;
import org.eclipse.jface.text.DefaultLineTracker;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentExtension;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.text.ILineTracker;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextStore;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.Region;



/**
 * A <code>ProjectionDocument</code> represents a projection of its master
 * document. The contents of a projection document is a sequence of fragments of
 * the master document, i.e. the projection document can be thought as being
 * constructed from the master document by not copying the whole master document
 * but omitting several ranges of the master document.
 * <p>
 * The projection document utilizes its master document as
 * <code>ITextStore</code>.
 * <p>
 * API in progress. Do not yet use.
 * 
 * @since 3.0
 */
public class ProjectionDocument extends AbstractDocument {
	
	/** The master document */
	private IDocument fMasterDocument;
	/** The master document as document extension */
	private IDocumentExtension fMasterDocumentExtension;
	/** The fragments' position category */
	private String fFragmentsCategory;
	/** The segment's position category */
	private String fSegmentsCategory;
	/** The document event issued by the master document */
	private DocumentEvent fMasterEvent;
	/** The document event issued and to be issued by the projection document */
	private ProjectionDocumentEvent fSlaveEvent;
	/** Indicates whether the projection document initiated a master document update or not */
	private boolean fIsUpdating= false;	
	/** Indicated whether the projection document is in auto expand mode nor not */
	private boolean fIsAutoExpanding= false;
	/** The position updater for the segments */
	private SegmentUpdater fSegmentUpdater;
	/** The position updater for the fragments */
	private FragmentUpdater fFragmentsUpdater;
	/** The projection mapping */
	private ProjectionMapping fMapping;
	
	/**
	 * Creates a projection document for the given master document.
	 *
	 * @param masterDocument the master document
	 * @param fragmentsCategory the document position category managing the master's fragments
	 * @param fragmentUpdater the fragment position updater of the master document
	 * @param segmentsCategory the document position category managing the segments
	 */
	public ProjectionDocument(IDocument masterDocument, String fragmentsCategory, FragmentUpdater fragmentUpdater, String segmentsCategory) {
		super();
		
		fMasterDocument= masterDocument;
		if (fMasterDocument instanceof IDocumentExtension) 
			fMasterDocumentExtension= (IDocumentExtension) fMasterDocument;
		
		fFragmentsCategory= fragmentsCategory;
		fFragmentsUpdater= fragmentUpdater;
		fSegmentsCategory= segmentsCategory;
		fMapping= new ProjectionMapping(masterDocument, fragmentsCategory, this, segmentsCategory);
		
		ITextStore s= new ProjectionTextStore(masterDocument, fMapping);
		ILineTracker tracker= new DefaultLineTracker();
		
		setTextStore(s);
		setLineTracker(tracker);
		
		completeInitialization();
		
		initializeProjection();
		tracker.set(s.get(0, s.getLength()));
	}
		
	private void internalError() {
		throw new IllegalStateException();
	}
	
	protected final Position[] getFragments() {
		try {
			return fMasterDocument.getPositions(fFragmentsCategory);
		} catch (BadPositionCategoryException e) {
			internalError();
		}
		// unreachable
		return null;
	}
	
	protected final Position[] getSegments() {
		try {
			return getPositions(fSegmentsCategory);
		} catch (BadPositionCategoryException e) {
			internalError();
		}
		// unreachable
		return null;
	}
	
	/**
	 * Returns the projection mapping used by this document.
	 * 
	 * @return the projection mapping used by this document
	 */
	public ProjectionMapping getProjectionMapping(){
		return fMapping;
	}
	
	/**
	 * Returns the master document of this projection document.
	 * 
	 * @return the master document of this projection document
	 */
	public IDocument getMasterDocument() {
		return fMasterDocument;
	}

	/**
	 * Initializes the projection document from the master document based on
	 * the master's fragments.
	 */
	private void initializeProjection() {
		
		try {
			
			addPositionCategory(fSegmentsCategory);
			fSegmentUpdater= new SegmentUpdater(fSegmentsCategory);
			addPositionUpdater(fSegmentUpdater);
			
			int offset= 0;
			Position[] fragments= getFragments();
			for (int i= 0; i < fragments.length; i++) {
				Fragment fragment= (Fragment) fragments[i];
				Segment segment= new Segment(offset, fragment.getLength());
				segment.fragment= fragment;
				addPosition(fSegmentsCategory, segment);
				offset += fragment.length;
			}
			
		} catch (BadPositionCategoryException x) {
			internalError();
		} catch (BadLocationException x) {
			internalError();
		}
	}
	
	private Segment createSegmentFor(Fragment fragment, int index) throws BadLocationException, BadPositionCategoryException {
		
		int offset= 0;
		if (index > 0) {
			Position[] segments= getSegments();
			Segment segment= (Segment) segments[index - 1];
			offset= segment.getOffset() + segment.getLength();
		}
		
		Segment segment= new Segment(offset, 0);
		segment.fragment= fragment;
		fragment.segment= segment;
		addPosition(fSegmentsCategory, segment);
		return segment;
	}
	
	/**
	 * Adds the given range of the master document to this projection document.
	 * 
	 * @param offsetInMaster offset of the master document range
	 * @param lengthInMaster length of the master document range
	 * @throws BadLocationException if the given range is invalid in the master document
	 */
	private void internalAddMasterDocumentRange(int offsetInMaster, int lengthInMaster) throws BadLocationException {
		
		if (lengthInMaster == 0)
			return;
		
		try {
			
			Position[] fragments= getFragments();
			int index= fMasterDocument.computeIndexInCategory(fFragmentsCategory, offsetInMaster);
			
			Fragment left= null;
			Fragment right= null;
			
			if (index < fragments.length) {
				if (offsetInMaster == fragments[index].offset)
					throw new IllegalArgumentException("overlaps with existing fragment"); 
				if (offsetInMaster + lengthInMaster == fragments[index].offset) 
					right= (Fragment) fragments[index];
			}
			
			if (0 < index && index <= fragments.length) {
				Fragment fragment= (Fragment) fragments[index - 1];
				if (fragment.includes(offsetInMaster))
					throw new IllegalArgumentException("overlaps with existing fragment");
				if (fragment.getOffset() + fragment.getLength() == offsetInMaster)
					left= fragment;
			}
			
			// check for neighboring fragment
			if (left != null && right != null) {
				
				int endOffset= right.getOffset() + right.getLength();
				left.setLength(endOffset - left.getOffset());
				left.segment.setLength(left.segment.getLength() + right.segment.getLength());
				
				removePosition(fSegmentsCategory, right.segment);
				fMasterDocument.removePosition(fFragmentsCategory, right);
				
			} else if (left != null) {
				int endOffset= offsetInMaster +lengthInMaster;
				left.setLength(endOffset - left.getOffset());
				left.segment.markForStretch();
				
			} else if (right != null) {
				right.setOffset(right.getOffset() - lengthInMaster);
				right.setLength(right.getLength() + lengthInMaster);
				right.segment.markForStretch();
				
			} else {
				// create a new segment
				Fragment fragment= new Fragment(offsetInMaster, lengthInMaster);
				fMasterDocument.addPosition(fFragmentsCategory, fragment);
				Segment segment= createSegmentFor(fragment, index);
				segment.markForStretch();
			}
			
			int offsetInSlave= fMapping.toImageOffset(offsetInMaster);
			Assert.isTrue(offsetInSlave != -1);
			
			ProjectionDocumentEvent event= new ProjectionDocumentEvent(this, offsetInSlave, 0, fMasterDocument.get(offsetInMaster, lengthInMaster));
			super.fireDocumentAboutToBeChanged(event);
			getTracker().replace(event.getOffset(), event.getLength(), event.getText());
			super.fireDocumentChanged(event);
			
		} catch (BadPositionCategoryException x) {
			internalError();
		}
	}
	
	/**
	 * Finds the fragment of the master document that represents the given range.
	 * 
	 * @param offsetInMaster the offset of the range in the master document
	 * @param lengthInMaster the length of the range in the master document
	 * @return the fragment representing the given master document range
	 */
	private Fragment findFragment(int offsetInMaster, int lengthInMaster) {
		Position[] fragments= getFragments();
		for (int i= 0; i < fragments.length; i++) {
			Fragment f= (Fragment) fragments[i];
			if (f.getOffset() <= offsetInMaster && offsetInMaster + lengthInMaster <= f.getOffset() + f.getLength())
				return f;
		}
		return null;
	}
	
	/**
	 * Removes the given range of the master document from this projection
	 * document.
	 * 
	 * @param offsetInMaster the offset of the range in the master document
	 * @param lengthInMaster the length of the range in the master document
	 * 
	 * @throws BadLocationException if the given range is not valid in the
	 *             master document
	 * @throws IllegalArgumentException if the given range is not projected in
	 *             this projection document or is not completely comprised by
	 *             an existing fragment
	 */
	private void internalRemoveMasterDocumentRange(int offsetInMaster, int lengthInMaster) throws BadLocationException {
		try {
			
			IRegion imageRegion= fMapping.toExactImageRegion(new Region(offsetInMaster, lengthInMaster));
			if (imageRegion == null)
				throw new IllegalArgumentException();
			
			Fragment fragment= findFragment(offsetInMaster, lengthInMaster);
			if (fragment == null)
				throw new IllegalArgumentException();
			
			if (fragment.getOffset() == offsetInMaster) {
				fragment.setOffset(offsetInMaster + lengthInMaster);
				fragment.setLength(fragment.getLength() - lengthInMaster);
			} else if (fragment.getOffset() + fragment.getLength() == offsetInMaster + lengthInMaster) {
				fragment.setLength(fragment.getLength() - lengthInMaster);
			} else {
				// split fragment into three fragments, let position updater remove it
				
				// add fragment for the region to be removed
				Fragment newFragment= new Fragment(offsetInMaster, lengthInMaster);
				Segment segment= new Segment(imageRegion.getOffset(), imageRegion.getLength());
				newFragment.segment= segment;
				segment.fragment= newFragment;
				fMasterDocument.addPosition(fFragmentsCategory, newFragment);
				addPosition(fSegmentsCategory, segment);
				
				// add fragment for the remainder right of the deleted range in the original fragment
				int offset= offsetInMaster + lengthInMaster;
				newFragment= new Fragment(offset, fragment.getOffset() + fragment.getLength() - offset);
				offset= imageRegion.getOffset() + imageRegion.getLength();
				segment= new Segment(offset, fragment.segment.getOffset() + fragment.segment.getLength() - offset);
				newFragment.segment= segment;
				segment.fragment= newFragment;
				fMasterDocument.addPosition(fFragmentsCategory, newFragment);
				addPosition(fSegmentsCategory, segment);
				
				// adjust length of initial fragment (the left one)
				fragment.setLength(offsetInMaster - fragment.getOffset());
				fragment.segment.setLength(imageRegion.getOffset() - fragment.segment.getOffset());
			}
			
			ProjectionDocumentEvent event= new ProjectionDocumentEvent(this, imageRegion.getOffset(), imageRegion.getLength(), null);
			super.fireDocumentAboutToBeChanged(event);
			getTracker().replace(event.getOffset(), event.getLength(), event.getText());
			super.fireDocumentChanged(event);
			
		} catch (BadPositionCategoryException x) {
			internalError();
		}
	}
	
	private IRegion[] computeUnprojectedMasterRegions(int offsetInMaster, int lengthInMaster) throws BadLocationException {
		
		IRegion[] fragments= null;
		IRegion imageRegion= fMapping.toImageRegion(new Region(offsetInMaster, lengthInMaster));
		if (imageRegion != null)
			fragments= fMapping.toExactOriginRegions(imageRegion);
		
		if (fragments == null || fragments.length == 0)
			return new IRegion[] { new Region(offsetInMaster, lengthInMaster) };
		
		List gaps= new ArrayList();
		
		IRegion region= fragments[0];
		if (offsetInMaster < region.getOffset())
			gaps.add(new Region(offsetInMaster, region.getOffset() - offsetInMaster));
		
		for (int i= 0; i < fragments.length - 1; i++) {
			IRegion left= fragments[i];
			IRegion right= fragments[i + 1];
			int leftEnd= left.getOffset() + left.getLength();
			if (leftEnd < right.getOffset())
				gaps.add(new Region(leftEnd, right.getOffset() - leftEnd));
		}
		
		region= fragments[fragments.length - 1];
		int leftEnd= region.getOffset() + region.getLength();
		int rightEnd= offsetInMaster + lengthInMaster;
		if (leftEnd < rightEnd)
			gaps.add(new Region(leftEnd, rightEnd - leftEnd));
		
		IRegion[] result= new IRegion[gaps.size()];
		gaps.toArray(result);
		return result;
	}
	
	/**
	 * Ensures that the given range of the master document is part of this
	 * projection document.
	 * 
	 * @param offsetInMaster the offset of the master document range
	 * @param lengthInMaster the length of the master document range
	 */
	public void addMasterDocumentRange(int offsetInMaster, int lengthInMaster) throws BadLocationException {
		
		IRegion[] gaps= computeUnprojectedMasterRegions(offsetInMaster, lengthInMaster);
		if (gaps == null)
			return;
		
		for (int i= 0; i < gaps.length; i++) {
			IRegion gap= gaps[i];
			internalAddMasterDocumentRange(gap.getOffset(), gap.getLength());
		}
	}
	
	/**
	 * Ensures that the given range of the master document is not part of this
	 * projection document.
	 * 
	 * @param offsetInMaster the offset of the master document range
	 * @param lengthInMaster the length of the master document range
	 */
	public void removeMasterDocumentRange(int offsetInMaster, int lengthInMaster) throws BadLocationException {
		
		IRegion[] fragments= null;
		IRegion imageRegion= fMapping.toImageRegion(new Region(offsetInMaster, lengthInMaster));
		if (imageRegion != null)
			fragments= fMapping.toExactOriginRegions(imageRegion);
		
		if (fragments == null || fragments.length == 0)
			return;
		
		for (int i= 0; i < fragments.length; i++) {
			IRegion fragment= fragments[i];
			internalRemoveMasterDocumentRange(fragment.getOffset(), fragment.getLength());
		}
	}
	
	/*
	 * @see org.eclipse.jface.text.IDocument#replace(int, int, java.lang.String)
	 */
	public void replace(int offset, int length, String text) throws BadLocationException {
		try {
			fIsUpdating= true;
			if (fMasterDocumentExtension != null)
				fMasterDocumentExtension.stopPostNotificationProcessing();
				
			super.replace(offset, length, text);
			
		} finally {
			fIsUpdating= false;
			if (fMasterDocumentExtension != null)
				fMasterDocumentExtension.resumePostNotificationProcessing();
		}
	}
	
	/*
	 * @see org.eclipse.jface.text.IDocument#set(java.lang.String)
	 */
	public void set(String text) {
		try {
			fIsUpdating= true;
			if (fMasterDocumentExtension != null)
				fMasterDocumentExtension.stopPostNotificationProcessing();
				
			super.set(text);
		
		} finally {
			fIsUpdating= false;
			if (fMasterDocumentExtension != null)
				fMasterDocumentExtension.resumePostNotificationProcessing();
		}
	}
	
	/**
	 * Transforms a document event of the master document into a projection
	 * document based document event.
	 * 
	 * @param masterEvent the master document event
	 * @return the slave document event
	 */
	private ProjectionDocumentEvent normalize(DocumentEvent masterEvent) throws BadLocationException {
		IRegion imageRegion= fMapping.toExactImageRegion(new Region(masterEvent.getOffset(), masterEvent.getLength()));
		if (imageRegion != null)
			return new ProjectionDocumentEvent(this, imageRegion.getOffset(), imageRegion.getLength(), masterEvent.getText(), masterEvent);
		else {
			
		}
		return null;
	}
	
	/**
	 * Ensures that when the master event effects this projection document, that the whole region described by the
	 * event is part of this projection document.
	 * 
	 * @param masterEvent the master document event
	 * @return <code>true</code> if masterEvent affects this projection document
	 * @throws BadLocationException in case the master event is not valid
	 */
	private boolean adaptProjectionToMasterChange(DocumentEvent masterEvent) throws BadLocationException {
		if (!fIsUpdating || fIsAutoExpanding) {
			if (fFragmentsUpdater.affectsPositions(masterEvent)) {
				addMasterDocumentRange(masterEvent.getOffset(), masterEvent.getLength());
				return true;				
			}
		}
		return false;
	}
	
	/**
	 * When called, this projection document is informed about a forthcoming
	 * change of its master document. This projection document checks whether
	 * the master document change affects it and if so informs all document
	 * listeners.
	 * 
	 * @param masterEvent the master document event
	 */
	public void masterDocumentAboutToBeChanged(DocumentEvent masterEvent) {
		try {
			
			boolean assertNotNull= adaptProjectionToMasterChange(masterEvent);
			fSlaveEvent= normalize(masterEvent);
			if (assertNotNull && fSlaveEvent == null)
				internalError();
			
			fMasterEvent= masterEvent;
			if (fSlaveEvent != null)
				delayedFireDocumentAboutToBeChanged();
		
		} catch (BadLocationException e) {
			internalError();
		}
	}
	
	/**
	 * When called, this projection document is informed about a change of its
	 * master document. If this projection document is affected it informs all
	 * of its document listeners.
	 * 
	 * @param masterEvent the master document event
	 */
	public void masterDocumentChanged(DocumentEvent masterEvent) {
		if ( !fIsUpdating && masterEvent == fMasterEvent) {
			if (fSlaveEvent != null) {
				try {
					getTracker().replace(fSlaveEvent.getOffset(), fSlaveEvent.getLength(), fSlaveEvent.getText());
					fireDocumentChanged(fSlaveEvent);
				} catch (BadLocationException e) {
					internalError();
				}
			} else {
				ensureWellFormedSegmentation();
			}
		}
	}
	
	/*
	 * @see org.eclipse.jface.text.AbstractDocument#fireDocumentAboutToBeChanged(org.eclipse.jface.text.DocumentEvent)
	 */
	protected void fireDocumentAboutToBeChanged(DocumentEvent event) {
		// delay it until there is a notification from the master document
		// at this point, it is expensive to construct the master document information
	}
	
	/**
	 * Fires the slave document event as about-to-be-changed event to all registered listeners.
	 */
	private void delayedFireDocumentAboutToBeChanged() {
		super.fireDocumentAboutToBeChanged(fSlaveEvent);
	}
	
	/**
	 * Ignores the given event and sends the semantically equal slave document event instead.
	 *
	 * @param event the event to be ignored
	 */
	protected void fireDocumentChanged(DocumentEvent event) {
		super.fireDocumentChanged(fSlaveEvent);
	}
	
	/*
	 * @see org.eclipse.jface.text.AbstractDocument#updateDocumentStructures(org.eclipse.jface.text.DocumentEvent)
	 */
	protected void updateDocumentStructures(DocumentEvent event) {
		super.updateDocumentStructures(event);
		ensureWellFormedSegmentation();
	}
	
	private void ensureWellFormedSegmentation() {
		Position[] segments= getSegments();
		for (int i= 0; i < segments.length; i++) {
			Segment segment= (Segment) segments[i];
			if (segment.isDeleted()) {
				try {
					removePosition(fSegmentsCategory, segment); 
					fMasterDocument.removePosition(fFragmentsCategory, segment.fragment);
				} catch (BadPositionCategoryException e) {
					internalError();
				}
			} else if (i < segments.length - 1) {
				Segment next= (Segment) segments[i + 1];
				if (next.isDeleted())
					continue;
				Fragment fragment= segment.fragment;
				if (fragment.getOffset() + fragment.getLength() == next.fragment.getOffset()) {
					// join fragments and their corresponding segments
					segment.setLength(segment.getLength() + next.getLength());
					fragment.setLength(fragment.getLength() + next.fragment.getLength());
					next.delete();
				}
			}
		}
	}

	/*
	 * @see IDocumentExtension#registerPostNotificationReplace(IDocumentListener, IDocumentExtension.IReplace)
	 */
	public void registerPostNotificationReplace(IDocumentListener owner, IDocumentExtension.IReplace replace) {
		if (!fIsUpdating)
			throw new UnsupportedOperationException();
		super.registerPostNotificationReplace(owner, replace);
	}
	
	/** 
	 * Sets the auto expand mode for this document.
	 */
	public void setAutoExpandMode(boolean autoExpandMode) {
		fIsAutoExpanding= autoExpandMode;
	}
}
