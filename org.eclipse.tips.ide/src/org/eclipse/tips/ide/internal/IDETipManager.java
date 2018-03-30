/*******************************************************************************
 * Copyright (c) 2018 Remain Software
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     wim.jongman@remainsoftware.com - initial API and implementation
 *******************************************************************************/
package org.eclipse.tips.ide.internal;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.eclipse.core.expressions.EvaluationResult;
import org.eclipse.core.expressions.Expression;
import org.eclipse.core.expressions.ExpressionConverter;
import org.eclipse.core.expressions.IEvaluationContext;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.preferences.ConfigurationScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.tips.core.ITipManager;
import org.eclipse.tips.core.Tip;
import org.eclipse.tips.core.TipProvider;
import org.eclipse.tips.core.internal.LogUtil;
import org.eclipse.tips.core.internal.TipManager;
import org.eclipse.tips.ui.internal.DefaultTipManager;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.services.IEvaluationService;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.prefs.BackingStoreException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Class to manage the tip providers and start the tip of the day UI.
 */
@SuppressWarnings("restriction")
public class IDETipManager extends DefaultTipManager {

	private TipSourceProvider fSourceProvider = new TipSourceProvider();

	private List<Integer> fReadTips = new ArrayList<>();

	private boolean fNewTips;

	private boolean fSourceProviderAdded;

	private static IDETipManager instance = new IDETipManager();

	/**
	 * @return the tip manager instance.
	 */
	public static synchronized ITipManager getInstance() {
		if (instance.isDisposed()) {
			instance = new IDETipManager();
		}
		return instance;
	}

	private IDETipManager() {
	}

	@Override
	public ITipManager register(TipProvider provider) {
		super.register(provider);
		load(provider);
		return this;
	}

	private void load(TipProvider provider) {
		Job job = new Job("Loading " + provider.getDescription()) {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				return provider.loadNewTips(monitor);
			}
		};
		job.addJobChangeListener(new ProviderLoadJobChangeListener(this, provider));
		job.schedule();
	}

	@Override
	public ITipManager open(boolean startUp) {
		if (isOpen()) {
			return this;
		}
		if (!fSourceProviderAdded) {
			IEvaluationService evaluationService = PlatformUI.getWorkbench().getService(IEvaluationService.class);
			evaluationService.addSourceProvider(fSourceProvider);
			fSourceProviderAdded = true;
		}
		fReadTips.addAll(Preferences.loadReadState());
		return super.open(startUp);
	}

	/**
	 * Saves the tip read status to disk.
	 * 
	 * @param pReadTips the tips to save
	 *
	 */
	private void saveReadState(List<Integer> pReadTips) {
		Job job = new Job("Tips save read state..") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				SubMonitor subMonitor = SubMonitor.convert(monitor, IProgressMonitor.UNKNOWN);
				subMonitor.setTaskName("Saving read tips..");
				IStatus status = Preferences.saveReadState(pReadTips);
				subMonitor.done();
				return status;
			}
		};
		job.schedule();
	}

	/**
	 * Calculates the new tip count to find if we need to expose the status trim
	 * tool item.
	 *
	 * @param newTips
	 */
	private void refreshUI() {
		boolean newTips = getProviders().stream().filter(p -> !p.getTips().isEmpty()).count() > 0;
		Job job = new Job("Tips status bar refresh..") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				setNewTips(newTips);
				return Status.OK_STATUS;
			}
		};
		job.schedule();
	}

	@Override
	public boolean isRunAtStartup() {
		return Preferences.isRunAtStartup();
	}

	@Override
	public TipManager setRunAtStartup(boolean runAtStartup) {
		Preferences.setRunAtStartup(runAtStartup);
		return this;
	}

	@Override
	public boolean mustServeReadTips() {
		return Preferences.isServeReadTips();
	}

	@Override
	public TipManager setServeReadTips(boolean serveRead) {
		Preferences.setServeReadTips(serveRead);
		return this;
	}

	@Override
	public ITipManager log(IStatus status) {
		if (status.matches(IStatus.ERROR | IStatus.WARNING)) {
			Bundle bundle = FrameworkUtil.getBundle(getClass());
			Platform.getLog(bundle).log(status);
		}
		if (System.getProperty("org.eclipse.tips.consolelog") != null) {
			System.out.println(status.toString());
		}
		return this;
	}

	@Override
	public boolean isRead(Tip tip) {
		if (fReadTips.contains(Integer.valueOf(tip.hashCode()))) {
			return true;
		}
		return false;
	}

	@Override
	public TipManager setAsRead(Tip tip) {
		if (!isRead(tip)) {
			fReadTips.add(Integer.valueOf(tip.hashCode()));
		}
		return this;
	}

	protected synchronized IDETipManager setNewTips(boolean newTips) {
		if (fNewTips != newTips) {
			fNewTips = newTips;
			fSourceProvider.setStatus(fNewTips);
		}
		return this;
	}

	@Override
	public void dispose() {
		try {
			refreshUI();
			saveReadState(Collections.unmodifiableList(fReadTips));
		} finally {
			super.dispose();
		}
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * The weight is determined by the enablement expression. If there is no
	 * enablement expression then the weight is 20. If there is a non matching
	 * enablement then the weight is 30. If there is a matching enablement then the
	 * weight is 10.
	 *
	 * @param provider the provider
	 *
	 * @return the weight
	 */
	@Override
	public int getPriority(TipProvider provider) {
		log(LogUtil.info("Evaluating expression: " + provider.getExpression()));
		int priority = doGetPriority(provider.getExpression());
		log(LogUtil.info("Evaluating expression done. Priority: " + priority));
		return priority;
	}

	private int doGetPriority(String expression) {
		if (expression == null) {
			return 20;
		}
		try {
			String myExpression = "<enablement>" + expression + "</enablement>";
			myExpression = "<?xml version=\"1.0\"?>" + myExpression;
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			Document doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(myExpression.getBytes()));
			Element element = (Element) doc.getElementsByTagName("enablement").item(0);
			Expression expressionObj = ExpressionConverter.getDefault().perform(element);
			final EvaluationResult result = expressionObj.evaluate(getEvaluationContext());
			if (result == EvaluationResult.TRUE) {
				return 10;
			} else {
				return 30;
			}
		} catch (Exception e) {
			log(LogUtil.error(e));
			return 20;
		}
	}

	/**
	 *
	 * @return Evaluation Context to evaluate core expression
	 */
	private static IEvaluationContext getEvaluationContext() {
		IEvaluationService evalService = PlatformUI.getWorkbench().getService(IEvaluationService.class);
		IEvaluationContext currentState = evalService.getCurrentState();
		return currentState;
	}
}