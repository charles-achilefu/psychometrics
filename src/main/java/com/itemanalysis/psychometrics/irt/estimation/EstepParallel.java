/**
 * Copyright 2014 J. Patrick Meyer
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.itemanalysis.psychometrics.irt.estimation;

import com.itemanalysis.psychometrics.distribution.DistributionApproximation;
import com.itemanalysis.psychometrics.irt.model.ItemResponseModel;

import java.util.Arrays;
import java.util.concurrent.RecursiveTask;

/**
 * Estep of the EM algorithm for estimating item parameters in MMLE. The computation is done in parallel if the
 * number of response vectors exceeds {@link #PARALLEL_THRESHOLD}.
 */
public class EstepParallel extends RecursiveTask<EstepEstimates> {

    private int start = 0;
    private int length = 0;
    private int nItems = 0;
    private int nPoints = 0;
    private int[] ncat = null;
    private ItemResponseVector[] responseVector = null;
    private DistributionApproximation latentDistribution = null;
    private ItemResponseModel[] irm = null;
    private static int PARALLEL_THRESHOLD = 250;//Use default of 250

    /**
     * Default constructor may be called recursively for parallel computations.
     *
     * @param responseVector response vectors for a given set of data
     * @param irm an array of item response models whose parameters are being estimated
     * @param latentDistribution latent distribution quadrature used for computing the marginal likelihood
     * @param start beginning index for the response vector. Manual calls should always be 0, recursive calls are done automatically.
     * @param length length of the response vector segment. This length is manually set to the length of teh response vector.
     *               Recursive calls use the value specified by {@link #PARALLEL_THRESHOLD}.
     */
    public EstepParallel(ItemResponseVector[] responseVector, ItemResponseModel[] irm, DistributionApproximation latentDistribution, int start, int length){
        this.responseVector = responseVector;
        this.irm = irm;
        this.latentDistribution = latentDistribution;
        this.nPoints = latentDistribution.getNumberOfPoints();
        this.nItems = irm.length;
        this.start = start;
        this.length = length;

        ncat = new int[nItems];
        for(int j=0;j<nItems;j++){
            ncat[j] = irm[j].getNcat();
        }

    }

    /**
     * Estep computation when the number of response vectors is less than {@link #PARALLEL_THRESHOLD} or when
     * the recursive algorithm has reached its stopping condition. In parallel processing, this method represents
     * each chunk that is processed in parallel.
     *
     * @return
     */
    protected EstepEstimates computeDirectly(){
        double response = 0;
        double point = 0.0;
        double density = 0.0;
        double value = 0.0;
        double freq = 0.0;
        EstepEstimates estepEstimates = new EstepEstimates(nItems, ncat, nPoints);

        double[] conditionalLikelihood = new double[nPoints];
        double marginalLikelihood = 0;

        for(int l=start;l<(start+length);l++){

            marginalLikelihood = 0;
            for(int t=0;t<nPoints;t++){
                point = latentDistribution.getPointAt(t);
                density = latentDistribution.getDensityAt(t);
                conditionalLikelihood[t] = conditionalLikelihood(point, responseVector[l]);
                marginalLikelihood += conditionalLikelihood[t]*density;
            }

            freq = responseVector[l].getFrequency();
            for(int t=0;t<nPoints;t++){
                density = latentDistribution.getDensityAt(t);

                //nk
                value = freq*conditionalLikelihood[t]*density/marginalLikelihood;
                estepEstimates.incrementNt(t, value);

                //rjkt
                for(int j=0;j<nItems;j++){
                    response = Byte.valueOf(responseVector[l].getResponseAt(j)).doubleValue();

                    if(response!=-1){//Do not count missing responses
                        for(int k=0;k<irm[j].getNcat();k++){

                            //TODO might need to change k to the item score weight??
                            value = 0;
                            if((int)response==k) value = freq*conditionalLikelihood[t]*density/marginalLikelihood; //value = freq*clike*density/mlike;

                            estepEstimates.incrementRjkt(j, k, t, value);
                        }
                    }



                }
            }

            estepEstimates.incrementLoglikelihood(freq*Math.log(marginalLikelihood));

        }//end l loop over response vectors

        return estepEstimates;
    }

    /**
     * Recusive computation of the Estep for parallel processing. It will aggregate all chunks that are processed
     * in parallel.
     *
     * @return values of nk, rjk, and the loglikelihood (without priors).
     */
    @Override
    protected EstepEstimates compute(){
        if(length<PARALLEL_THRESHOLD){
            return computeDirectly();
        }else{
            int split = length/2;
            EstepParallel estep1 = new EstepParallel(responseVector, irm, latentDistribution, start, split);
            EstepParallel estep2 = new EstepParallel(responseVector, irm, latentDistribution, start+split, length-split);
            estep1.fork();
            EstepEstimates sum2 = estep2.compute();
            EstepEstimates sum1 = estep1.join();
            return sum2.add(sum1);
        }
    }

    /**
     * Computes the conditional likelihood of a response vector at the point given by quadPoint.
     *
     * @param quadPoint a quadrature points (i.e. theta value)
     * @param responseVector a response vector
     */
    private double conditionalLikelihood(double quadPoint, ItemResponseVector responseVector){
        int response = 0;
        double value = 1.0;
        for(int j=0;j<nItems;j++){

            response = Byte.valueOf(responseVector.getResponseAt(j)).intValue();
            if(response!=-1){

                value *= irm[j].probability(quadPoint, response);

//Not sure why I was using a loop here, when I could have just used the response directly
//                for(int k=0;k<ncat[j];k++){
//                    //TODO might need to change  k to the item score weight
//                    if(response==k) value *= irm[j].probability(quadPoint, k);
//
//                }

            }


        }
        return value;
    }

}
