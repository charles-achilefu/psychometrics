/*
 * Copyright 2012 J. Patrick Meyer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.itemanalysis.psychometrics.polycor;

import com.itemanalysis.psychometrics.statistics.TwoWayTable;

/**
 *
 * @author J. Patrick Meyer
 */
public abstract class AbstractPolychoricCorrelation implements PolychoricCorrelation{

    protected double rho = Double.NaN;

    protected boolean rhoComputed = false;

    protected TwoWayTable table = null;

    public AbstractPolychoricCorrelation(){
        table = new TwoWayTable();
    }

    /**
     * Method for incrementally updating state of frequency table.
     *
     * @param x
     * @param y
     */
    public void addValue(int x, int y){
        table.addValue(x, y);
    }

    /**
     * Method for getting rho when using incremental update. Result
     * may change if addValue is called between calls to getResult.
     *
     * @return polychorioc correlation
     */
    public double getResult(){
        double[][] d = table.getTable();
        compute(d);
        return value();
    }


}