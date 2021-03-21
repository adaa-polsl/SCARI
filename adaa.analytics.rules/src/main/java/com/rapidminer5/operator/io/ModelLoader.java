/*
 *  RapidMiner
 *
 *  Copyright (C) 2001-2014 by RapidMiner and the contributors
 *
 *  Complete list of developers available at our web site:
 *
 *       http://rapidminer.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see http://www.gnu.org/licenses/.
 */
package com.rapidminer5.operator.io;

import com.rapidminer.operator.*;
import com.rapidminer.operator.io.AbstractModelLoader;
import com.rapidminer.operator.io.ExampleSource;
import com.rapidminer.parameter.ParameterType;
import com.rapidminer.parameter.ParameterTypeFile;
import com.rapidminer.parameter.UndefinedParameterError;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Reads a {@link com.rapidminer.operator.Model} from a file that was generated
 * by an operator like {@link com.rapidminer.operator.learner.Learner} in a
 * previous process. Once a model is generated, it can be applied several
 * times to newly acquired data using a model loader, an {@link ExampleSource},
 * and a {@link com.rapidminer.operator.ModelApplier}.
 * 
 * @see com.rapidminer.operator.Model
 * @see com.rapidminer.operator.ModelApplier
 * @author Simon Fischer, Ingo Mierswa
 */
public class ModelLoader extends AbstractModelLoader {


	/** The parameter name for &quot;Filename containing the model to load.&quot; */
	public static final String PARAMETER_MODEL_FILE = "model_file";
	
	public ModelLoader(OperatorDescription description) {
		super(description);
	}
	
	/** Reads the model from disk and returns it. */
	@Override
	public Model read() throws OperatorException {
		getParameter(PARAMETER_MODEL_FILE);
		AbstractIOObject.InputStreamProvider inputStreamProvider = new AbstractIOObject.InputStreamProvider() {
//			@Override
			public InputStream getInputStream() throws IOException {
				try {
					return getParameterAsInputStream(PARAMETER_MODEL_FILE);
				} catch (UndefinedParameterError e) {
					throw new IOException(e);
				} catch (UserError e) {
					throw new IOException(e);
				}
			}
		};			
		IOObject model;
		try {
			model = AbstractIOObject.read(inputStreamProvider);
		} catch (IOException e) {
			throw new UserError(this, e, 302, getParameter(PARAMETER_MODEL_FILE), e);
		}
		if (!(model instanceof Model)) {
			throw new UserError(this, 942, new Object[] { getParameter(PARAMETER_MODEL_FILE), "Model", model.getClass().getSimpleName() });
		}

		return (Model)model;
	}

	@Override
	public List<ParameterType> getParameterTypes() {
		List<ParameterType> types = super.getParameterTypes();
		types.add(new ParameterTypeFile(PARAMETER_MODEL_FILE, "Filename containing the model to load.", "mod", false));
		return types;
	}

}
